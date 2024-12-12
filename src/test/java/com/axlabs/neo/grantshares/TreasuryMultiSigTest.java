package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoGetNep17Balances;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
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

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.IS_PAUSED;
import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.PAUSE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.EVE;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.map;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = {GrantSharesGov.class, GrantSharesTreasury.class},
              blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class TreasuryMultiSigTest {
    private static final int NEO_MAX_AMOUNT = 100;
    private static final int GAS_MAX_AMOUNT = 10000;
    private static final int MULTI_SIG_THRESHOLD_RATIO = 50;

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static GrantSharesGovContract gov;
    static GrantSharesTreasuryContract treasury;
    static Account alice; // Set to be a DAO member.
    static Account bob; // Set to be a funder.
    static Account charlie; // Set to be a DAO member.
    static Account denise;
    static Account eve;
    static Account deniseAndEve; // Set to be a funder.

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
        Account bob = ext.getAccount(BOB);
        Account denise = ext.getAccount(DENISE);
        Account eve = ext.getAccount(EVE);
        Account multiSigFunder = Account.createMultiSigAccount(
                asList(denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey()), 2);
        ContractParameter funders = array(
                array(bob.getScriptHash(), // bob
                        array(bob.getECKeyPair().getPublicKey())),
                array(multiSigFunder.getScriptHash(), // denise, eve
                        array(denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey()))
        );

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
        // contracts
        gov = new GrantSharesGovContract(
                ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        treasury = new GrantSharesTreasuryContract(
                ext.getDeployedContract(GrantSharesTreasury.class).getScriptHash(), neow3j);

        // accounts
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
        eve = ext.getAccount(EVE);
        deniseAndEve = Account.createMultiSigAccount(
                asList(denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey()), 2);

        // fund the treasury
        Hash256 txHash = new GasToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(100))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    @Test
    @Order(0)
    public void execute_proposal_with_modify_threshold() throws Throwable {
        ContractParameter intent = IntentParam.setFundersMultiSigThresholdRatioProposal(treasury.getScriptHash(), 51);
        String offchainUri = "execute_proposal_with_modify_threshold";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, charlie, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, charlie);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id)
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("ThresholdChanged"));
        assertThat(treasury.getFundersMultiSigThresholdRatio(), is(51));
    }

    @Test
    @Order(0)
    public void calc_funders_multisig_threshold() throws Throwable {
        int th = treasury.calcFundersMultiSigAddressThreshold();
        assertThat(th, is(2));
    }

    @Test
    @Order(0)
    public void calc_funders_multisig_address() throws Throwable {
        Hash160 hash = treasury.calcFundersMultiSigAddress();
        assertThat(hash, is(createMultiSigAccount(2, bob, denise, eve).getScriptHash()));
    }

    @Test
    @Order(0)
    public void fail_execute_drain_on_unpaused_contract() {
        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> treasury.drain().signers(AccountSigner.calledByEntry(alice)).sign());
        assertTrue(e.getMessage().endsWith("Contract is not paused"));
    }

    @Order(10)
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

    @Order(11)
    @Test
    public void fail_execute_drain_with_non_funder() {
        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> treasury.drain().signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Not authorized"));
    }

    @Order(12)
    @Test
    public void successfully_drain_with_funders_account() throws Throwable {
        Account membersAccount = createMultiSigAccount(2, bob, denise, eve);
        Hash256 tx = treasury.drain()
                .signers(AccountSigner.none(bob), AccountSigner.calledByEntry(membersAccount))
                .getUnsignedTransaction()
                .addWitness(bob)
                .addMultiSigWitness(membersAccount.getVerificationScript(), bob, denise)
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        List<NeoGetNep17Balances.Nep17Balance> balances =
                neow3j.getNep17Balances(treasury.getScriptHash()).send().getBalances().getBalances();
        balances.stream().map(b -> Long.valueOf(b.getAmount())).reduce(Long::sum)
                .ifPresent(s -> assertThat(s, is(0L)));
    }
}
