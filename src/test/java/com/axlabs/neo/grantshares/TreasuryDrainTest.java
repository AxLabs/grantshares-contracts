package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoGetNep17Balances;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CALC_FUNDERS_MULTI_SIG_ACCOUNT;
import static com.axlabs.neo.grantshares.TestHelper.CHANGE_THRESHOLD;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.TestHelper.DRAIN;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.IS_PAUSED;
import static com.axlabs.neo.grantshares.TestHelper.PAUSE;
import static com.axlabs.neo.grantshares.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
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
    @Order(0)
    public void execute_proposal_with_modify_threshold() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), CHANGE_THRESHOLD,
                        array(50)));
        String discUrl = "execute_proposal_with_modify_threshold";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, charlie, intents, discUrl);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, charlie);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("ThresholdChanged"));
    }

    @Test
    @Order(1)
    public void calc_funders_multisig_account() throws Throwable {
        String account = treasury.callInvokeFunction(CALC_FUNDERS_MULTI_SIG_ACCOUNT)
                .getInvocationResult().getStack().get(0).getAddress();
        assertThat(account, is(createMultiSigAccount(1, bob, denise).getAddress()));
    }

    @Test
    @Order(1)
    public void fail_execute_drain_on_unpaused_contract() throws Throwable {
        String exception = treasury.invokeFunction(DRAIN)
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
        String exception = treasury.invokeFunction(DRAIN).signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorized"));
    }

    @Order(3)
    @Test
    public void fail_execute_proposal_with_drain_contract() throws Throwable {
        ContractParameter intents = array(array(treasury.getScriptHash(), DRAIN, array()));
        String discUrl = "execute_proposal_with_drain_contract";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, discUrl);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Account membersAccount = createMultiSigAccount(1, bob, denise);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorized"));
    }

    @Order(4)
    @Test
    public void successfully_drain_with_funders_account() throws Throwable {
        Account membersAccount = createMultiSigAccount(1, bob, denise);
        Transaction tx = treasury.invokeFunction(DRAIN).signers(
                        AccountSigner.none(bob),
                        AccountSigner.calledByEntry(membersAccount).setAllowedContracts(treasury.getScriptHash()))
                .getUnsignedTransaction();
        tx.addWitness(Witness.create(tx.getHashData(), bob.getECKeyPair()));
        tx.addMultiSigWitness(membersAccount.getVerificationScript(), bob);
        Hash256 txHash = tx.send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        List<NeoGetNep17Balances.Nep17Balance> balances =
                neow3j.getNep17Balances(treasury.getScriptHash()).send().getBalances().getBalances();
        balances.stream().map(b -> Long.valueOf(b.getAmount())).reduce(Long::sum)
                .ifPresent(s -> assertThat(s, is(0L)));
    }
}
