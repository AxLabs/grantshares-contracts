package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.*;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static com.axlabs.neo.grantshares.TestHelper.*;
import static io.neow3j.types.ContractParameter.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = {GrantSharesGov.class, GrantSharesTreasury.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TreasuryDrainTest {
    private static final int NEO_MAX_AMOUNT = 100;
    private static final int GAS_MAX_AMOUNT = 10000;
    private static final int MULTI_SIG_THRESHOLD_RATIO = 100;

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static SmartContract gov;
    static SmartContract treasury;
    static Account alice; // Set to be a DAO member.
    static Account bob; // Set to be a funder.
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a funder.

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfigGov() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE)));
        return config;
    }

    @DeployConfig(GrantSharesTreasury.class)
    public static DeployConfiguration deployConfigTreasury(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        // owner
        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);
        // funders
        ContractParameter funders = array(
                ext.getAccount(BOB).getECKeyPair().getPublicKey().getEncoded(true),
                ext.getAccount(DENISE).getECKeyPair().getPublicKey().getEncoded(true));
        // whitelisted tokens
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT);
        tokens.put(GasToken.SCRIPT_HASH, GAS_MAX_AMOUNT);
        ContractParameter tokensParam = map(tokens);

        config.setDeployParam(array(gov.getScriptHash(), funders, tokensParam, MULTI_SIG_THRESHOLD_RATIO));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = ext.getDeployedContract(GrantSharesGov.class);
        treasury = ext.getDeployedContract(GrantSharesTreasury.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
        Hash256 txHash = new GasToken(neow3j).transfer(
                        bob, treasury.getScriptHash(), BigInteger.valueOf(100))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    @Test
    @Order(1)
    public void fail_execute_drain_on_unpaused_contract() throws Throwable {
        String exception = treasury.invokeFunction(DRAIN, integer(1))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is not paused"));
    }

    @Order(2)
    @Test
    public void succeed_pausing_contract() throws Throwable {
        assertFalse(treasury.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());

        Account membersAccount = createMultiSigAccount(1, alice, charlie);
        Transaction tx = gov.invokeFunction(PAUSE)
                .signers(AccountSigner.none(bob), AccountSigner.calledByEntry(membersAccount))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), bob.getECKeyPair()))
                .addMultiSigWitness(membersAccount.getVerificationScript(), alice)
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);

        assertTrue(treasury.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());
    }

    @Order(3)
    @Test
    public void fail_execute_drain_with_non_funder() throws IOException {
        String exception = treasury.invokeFunction(DRAIN, integer(1)).signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorized"));
    }

    @Order(3)
    @Test
    public void execute_proposal_with_drain_contract() throws Throwable {
        ContractParameter intents = array(array(treasury.getScriptHash(), DRAIN, array(integer(1))));
        String desc = "execute_proposal_with_drain_contract";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Account membersAccount = createMultiSigAccount(2, bob, denise);
        Transaction tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob),
                        AccountSigner.calledByEntry(membersAccount).setAllowedContracts(treasury.getScriptHash()))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), bob.getECKeyPair()))
                .addMultiSigWitness(membersAccount.getVerificationScript(), denise, bob)
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(txHash).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("Transfer"));
        assertThat(n.getContract(), is(GasToken.SCRIPT_HASH));
        assertThat(n.getState().getList().get(0).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n.getState().getList().get(1).getAddress(),
                is(membersAccount.getAddress()));
        assertThat(n.getState().getList().get(2).getInteger(), is(BigInteger.valueOf(100)));
    }
}
