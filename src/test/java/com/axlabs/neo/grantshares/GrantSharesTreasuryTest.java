package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.EVE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.IS_PAUSED;
import static com.axlabs.neo.grantshares.util.TestHelper.PAUSE;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.UNPAUSE;
import static com.axlabs.neo.grantshares.util.TestHelper.UPDATE_CONTRACT;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = {GrantSharesGov.class, GrantSharesTreasury.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesTreasuryTest {

    private static final BigInteger NEO_MAX_AMOUNT = BigInteger.valueOf(100);
    private static final BigInteger NEO_MAX_AMOUNT_CHANGED = BigInteger.valueOf(1000);
    private static final BigInteger GAS_MAX_AMOUNT = BigInteger.valueOf(10000);
    private static final int MULTI_SIG_THRESHOLD_RATIO = 100;
    private final static Path TESTCONTRACT_NEF_FILE = Paths.get("TestContract.nef");
    private final static Path TESTCONTRACT_MANIFEST_FILE =
            Paths.get("TestGrantSharesTreasury.manifest.json");
    private final static String ASSERT_EXCEPTION = "ASSERT is executed with false result";

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
        ContractParameter funders = array(array(bob.getScriptHash(), array(bob.getECKeyPair().getPublicKey())));

        // whitelisted tokens
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT.intValue());
        tokens.put(GasToken.SCRIPT_HASH, GAS_MAX_AMOUNT.intValue());
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
        Hash256 tx = new GasToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(100))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    @Test
    @Order(0)
    public void getFunders() throws IOException {
        Map<Hash160, List<ECPublicKey>> funders = treasury.getFunders();
        assertThat(funders.size(), is(1));
        assertThat(funders.get(bob.getScriptHash()).get(0), is(bob.getECKeyPair().getPublicKey()));
    }

    @Test
    @Order(0)
    public void getWhitelistedTokens() throws IOException {
        Map<Hash160, BigInteger> tokens = treasury.getWhitelistedTokens();
        assertThat(tokens.size(), is(2));
        assertThat(tokens.keySet(), containsInAnyOrder(NeoToken.SCRIPT_HASH, GasToken.SCRIPT_HASH));
        assertThat(tokens.values(), containsInAnyOrder(NEO_MAX_AMOUNT, GAS_MAX_AMOUNT));
    }

    @Test
    @Order(0)
    public void succeed_voting_on_committee_member() throws Throwable {
        NeoToken neo = new NeoToken(neow3j);
        // register alice as candidate (is already a committee member by default in neo-express)
        Hash256 hash = neo.registerCandidate(alice.getECKeyPair().getPublicKey())
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(hash, neow3j);
        // vote for alice
        hash = neo.vote(alice, alice.getECKeyPair().getPublicKey())
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(hash, neow3j);
        assertThat(neo.getCandidates().get(alice.getECKeyPair().getPublicKey()).intValue(), is(1000));

        // fund treasury
        hash = new NeoToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(100))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(hash, neow3j);
        // vote on Alice with treasury
        hash = treasury.voteCommitteeMemberWithLeastVotes().signers(AccountSigner.none(bob)).sign().send()
                .getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(hash, neow3j);
        assertThat(neo.getCandidates().get(alice.getECKeyPair().getPublicKey()).intValue(), is(1100));
    }

    @Test
    @Order(0)
    public void fail_calling_add_whitelisted_token_directly() throws IOException {
        String exception = treasury.addWhitelistedToken(GasToken.SCRIPT_HASH, 1)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_calling_remove_whitelisted_token_directly() throws IOException {
        String exception = treasury.removeWhitelistedToken(GasToken.SCRIPT_HASH)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_calling_add_funder_directly() throws Exception {
        String exception = treasury.addFunder(alice.getScriptHash(), alice.getECKeyPair().getPublicKey())
                .signers(AccountSigner.calledByEntry(alice)).callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_calling_remove_funder_directly() throws Exception {
        String exception = treasury.removeFunder(charlie.getScriptHash())
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_funding_treasury_with_non_funder() throws Throwable {
        InvocationResult res = new GasToken(neow3j).transfer(alice, treasury.getScriptHash(), BigInteger.valueOf(10))
                .signers(AccountSigner.calledByEntry(alice)).callInvokeScript().getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString(ASSERT_EXCEPTION));
    }

    @Test
    @Order(0)
    public void fail_execute_update_contract_directly() throws Throwable {
        File nefFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_NEF_FILE.toString()).toURI());
        NefFile nef = NefFile.readFromFile(nefFile);

        File manifestFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_MANIFEST_FILE.toString()).toURI());
        ContractManifest manifest = getObjectMapper()
                .readValue(manifestFile, ContractManifest.class);
        byte[] manifestBytes = getObjectMapper().writeValueAsBytes(manifest);

        ContractParameter data = string("update contract");

        String exception =
                treasury.invokeFunction(UPDATE_CONTRACT, byteArray(nef.toArray()), byteArray(manifestBytes), data)
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_release_tokens_with_non_whitelisted_token() throws Throwable {
        final Hash160 someToken = new Hash160("1a1512528147558851b39c2cd8aa47da7418aba1");
        ContractParameter intent = IntentParam.releaseTokenProposal(treasury.getScriptHash(), someToken,
                alice.getScriptHash(), BigInteger.TEN);
        String offchainUri = "fail_release_tokens_with_non_whitelisted_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();

        assertThat(exception, containsString("Token not whitelisted"));
    }

    @Test
    @Order(0)
    public void fail_release_tokens_with_to_high_amount() throws Throwable {
        ContractParameter intent = IntentParam.releaseTokenProposal(treasury.getScriptHash(), GasToken.SCRIPT_HASH,
                alice.getScriptHash(), GAS_MAX_AMOUNT.add(BigInteger.ONE));
        String offchainUri = "fail_release_tokens_with_to_high_amount";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Above token's max funding amount"));
    }

    @Test
    @Order(0)
    public void fail_calling_release_tokens_directly() throws IOException {
        String exception = treasury.releaseTokens(GasToken.SCRIPT_HASH, alice.getScriptHash(), BigInteger.ONE)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_adding_invalid_funder() throws Throwable {
        ContractParameter intent = new IntentParam(treasury.getScriptHash(), "addFunder",
                byteArray("3ff68d232a60f23a5805b8c40f7e61747f"), array(asList(alice.getECKeyPair().getPublicKey())));
        String offchainUri = "fail_adding_invalid_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid funder hash"));
    }

    @Test
    @Order(0)
    public void fail_adding_funder_with_no_public_keys() throws Throwable {
        ContractParameter intent = IntentParam.addFunderProposal(treasury.getScriptHash(), alice.getScriptHash());
        String offchainUri = "fail_adding_funder_with_no_public_keys";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("List of public keys is empty"));
    }

    @Test
    @Order(0)
    public void fail_adding_funder_with_invalid_public_key() throws Throwable {
        IntentParam intent = new IntentParam(treasury.getScriptHash(), "addFunder", hash160(alice.getScriptHash()),
                array(publicKey(alice.getECKeyPair().getPublicKey()),
                        byteArray("03dab84c1243ec01ab2500e1a8c7a1546a26d734628180b0cf64e72bf776")));
        String offchainUri = "fail_adding_funder_with_invalid_public_key";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid public key"));
    }

    @Test
    @Order(0)
    public void fail_setting_funders_multisig_threshold_ratio_with_invalid_value() throws Throwable {
        IntentParam intent1 = IntentParam.setFundersMultiSigThresholdRatioProposal(treasury.getScriptHash(), -5);
        String offchainUri1 = "fail_setting_funders_multisig_threshold_ratio_with_invalid_value1";
        IntentParam intent2 = IntentParam.setFundersMultiSigThresholdRatioProposal(treasury.getScriptHash(), 110);
        String offchainUri2 = "fail_setting_funders_multisig_threshold_ratio_with_invalid_value2";

        // 1. Create and endorse proposal
        int id1 = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent1), offchainUri1);
        int id2 = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent2), offchainUri2);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id1, alice);
        voteForProposal(gov, neow3j, id2, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id1)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid threshold ratio"));
        exception = gov.execute(id2)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid threshold ratio"));
    }

    @Test
    @Order(0)
    public void fail_adding_whitelisted_token_with_invalid_token() throws Throwable {
        ContractParameter intent = new IntentParam(treasury.getScriptHash(), "addWhitelistedToken",
                byteArray("3ff68d232a60f23a5805b8c40f7e61747f"), integer(100));
        String offchainUri = "fail_adding_whitelisted_token_with_invalid_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid token hash"));
    }

    @Test
    @Order(0)
    public void fail_adding_whitelisted_token_with_invalid_max_funding_amount() throws Throwable {
        ContractParameter intent = IntentParam.addWhitelistedTokenProposal(treasury.getScriptHash(),
                NeoToken.SCRIPT_HASH, BigInteger.ONE.negate());
        String offchainUri = "fail_adding_whitelisted_token_with_invalid_max_funding_amount";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid max funding amount"));
    }

    @Test
    @Order(1)
    public void execute_proposal_with_add_single_sig_funder() throws Throwable {
        IntentParam intent = IntentParam.addFunderProposal(treasury.getScriptHash(), alice.getScriptHash(),
                alice.getECKeyPair().getPublicKey());
        String offchainUri = "execute_proposal_with_add_single_sig_funder";

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
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("FunderAdded"));

        Map<Hash160, List<ECPublicKey>> funders = treasury.getFunders();
        assertThat(funders.size(), is(2));
    }

    @Test
    @Order(2)
    public void execute_proposal_with_add_multi_sig_funder() throws Throwable {
        Account denise = ext.getAccount(DENISE);
        Account eve = ext.getAccount(EVE);
        Account multiSigFunder = Account.createMultiSigAccount(
                asList(denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey()), 1);
        IntentParam intent = IntentParam.addFunderProposal(treasury.getScriptHash(), multiSigFunder.getScriptHash(),
                denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey());
        String offchainUri = "execute_proposal_with_add_multi_sig_funder";

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
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("FunderAdded"));

        Map<Hash160, List<ECPublicKey>> funders = treasury.getFunders();
        assertThat(funders.size(), is(3));
        assertThat(funders.get(multiSigFunder.getScriptHash()), containsInAnyOrder(
                denise.getECKeyPair().getPublicKey(), eve.getECKeyPair().getPublicKey()));
    }

    @Test
    @Order(3)
    public void fail_adding_already_added_funder() throws Throwable {
        ContractParameter intent = IntentParam.addFunderProposal(treasury.getScriptHash(), alice.getScriptHash(),
                alice.getECKeyPair().getPublicKey());
        String offchainUri = "fail_adding_already_added_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Already a funder"));
    }

    @Test
    @Order(4)
    public void execute_proposal_with_remove_funder() throws Throwable {
        ContractParameter intent = IntentParam.removeFunderProposal(treasury.getScriptHash(), alice.getScriptHash());
        String offchainUri = "execute_proposal_with_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("FunderRemoved"));

        Map<Hash160, List<ECPublicKey>> funders = treasury.getFunders();
        assertThat(funders.size(), is(2));
    }

    @Test
    @Order(5)
    public void fail_removing_nonexistant_funder() throws Throwable {
        IntentParam intent = IntentParam.removeFunderProposal(treasury.getScriptHash(), charlie.getScriptHash());
        String offchainUri = "fail_execution_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not a funder"));
    }

    @Test
    @Order(10)
    public void execute_proposal_with_remove_whitelisted_token() throws Throwable {
        IntentParam intent = IntentParam.removeWhitelistedTokenProposal(treasury.getScriptHash(), NeoToken.SCRIPT_HASH);
        String offchainUri = "execute_proposal_with_remove_whitelisted_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenRemoved"));

        Map<Hash160, BigInteger> tokens = treasury.getWhitelistedTokens();
        assertThat(tokens.size(), is(1));
    }

    @Test
    @Order(11)
    public void fail_funding_treasury_with_non_whitelisted_token() throws Throwable {
        InvocationResult res = new NeoToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(10))
                .callInvokeScript().getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString(ASSERT_EXCEPTION));
    }

    @Test
    @Order(12)
    public void execute_proposal_with_add_whitelisted_token() throws Throwable {
        ContractParameter intent = IntentParam.addWhitelistedTokenProposal(treasury.getScriptHash(),
                NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT_CHANGED);
        String offchainUri = "execute_proposal_with_add_whitelisted_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenAdded"));

        Map<Hash160, BigInteger> tokens = treasury.getWhitelistedTokens();
        assertThat(tokens.size(), is(2));
        assertThat(tokens.keySet(), containsInAnyOrder(NeoToken.SCRIPT_HASH, GasToken.SCRIPT_HASH));
    }

    @Test
    @Order(13)
    public void execute_proposal_with_change_whitelisted_token_max_amount() throws Throwable {
        Map<Hash160, BigInteger> tokens = treasury.getWhitelistedTokens();
        assertThat(tokens.get(NeoToken.SCRIPT_HASH), is(NEO_MAX_AMOUNT_CHANGED));

        ContractParameter intent = IntentParam.addWhitelistedTokenProposal(treasury.getScriptHash(),
                NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT);
        String offchainUri = "execute_proposal_with_change_whitelisted_token_max_amount";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id)
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenAdded"));

        tokens = treasury.getWhitelistedTokens();
        assertThat(tokens.size(), is(2));
        assertThat(tokens.keySet(), containsInAnyOrder(GasToken.SCRIPT_HASH, NeoToken.SCRIPT_HASH));
        assertThat(tokens.values(), containsInAnyOrder(GAS_MAX_AMOUNT, NEO_MAX_AMOUNT));
    }

    @Test
    @Order(14)
    public void funding_treasury_with_whitelisted_token_and_funder() throws Throwable {
        GasToken gas = new GasToken(neow3j);
        int initialValue = gas.getBalanceOf(treasury.getScriptHash()).intValue();
        Hash256 tx = gas.transfer(bob, treasury.getScriptHash(), BigInteger.ONE)
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("Transfer"));
        assertThat(n.getContract(), is(GasToken.SCRIPT_HASH));
        assertThat(n.getState().getList().get(0).getAddress(), is(bob.getAddress()));
        assertThat(n.getState().getList().get(1).getAddress(),
                is(treasury.getScriptHash().toAddress()));
        assertThat(n.getState().getList().get(2).getInteger(), is(BigInteger.ONE));

        assertThat(gas.getBalanceOf(treasury.getScriptHash()).intValue(), is(initialValue + 1));
    }

    @Test
    @Order(15)
    public void execute_proposal_with_release_tokens() throws Throwable {
        Account acc = Account.create();
        final BigInteger fundingAmount = BigInteger.TEN;
        ContractParameter intent = IntentParam.releaseTokenProposal(treasury.getScriptHash(), GasToken.SCRIPT_HASH,
                acc.getScriptHash(), fundingAmount);
        String offchainUri = "execute_proposal_with_release_tokens";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("Transfer"));

        assertThat(new GasToken(neow3j).getBalanceOf(acc), is(fundingAmount));
    }

    @Order(20)
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

    @Order(21)
    @Test
    public void fail_add_funder_on_paused_contract() throws Throwable {
        String exception = treasury.addFunder(alice.getScriptHash(), alice.getECKeyPair().getPublicKey())
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(22)
    @Test
    public void fail_remove_funder_on_paused_contract() throws Throwable {
        String exception = treasury.removeFunder(alice.getScriptHash())
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(23)
    @Test
    public void fail_add_whitelisted_token_on_paused_contract() throws Throwable {
        String exception = treasury.addWhitelistedToken(GasToken.SCRIPT_HASH, 1)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(24)
    @Test
    public void fail_remove_whitelisted_token_on_paused_contract() throws Throwable {
        String exception = treasury.removeWhitelistedToken(GasToken.SCRIPT_HASH)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(25)
    @Test
    public void fail_release_tokens_on_paused_contract() throws Throwable {
        String exception = treasury.releaseTokens(GasToken.SCRIPT_HASH, alice.getScriptHash(), BigInteger.ONE)
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(26)
    @Test
    public void fail_vote_on_committee_member_on_paused_contract() throws Throwable {
        String exception = treasury.voteCommitteeMemberWithLeastVotes()
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(27)
    @Test
    public void fail_update_contract_on_paused_contract() throws Throwable {
        String exception = gov.updateContract(new byte[]{0x01, 0x02, 0x03}, "the manifest", null).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(28)
    @Test
    public void fail_funding_treasury_on_paused_contract() throws Throwable {
        InvocationResult res = new GasToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(10))
                .signers(AccountSigner.calledByEntry(bob)).callInvokeScript().getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString(ASSERT_EXCEPTION));
    }

    @Order(29)
    @Test
    public void succeed_unpausing_contract() throws Throwable {
        assertTrue(treasury.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());

        Account membersAccount = createMultiSigAccount(1, alice, charlie);
        Transaction tx = gov.invokeFunction(UNPAUSE)
                .signers(AccountSigner.none(bob), AccountSigner.calledByEntry(membersAccount))
                .getUnsignedTransaction();
        Hash256 txHash = tx
                .addWitness(Witness.create(tx.getHashData(), bob.getECKeyPair()))
                .addMultiSigWitness(membersAccount.getVerificationScript(), alice)
                .send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);

        assertFalse(treasury.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());
    }

    @Test
    @Order(Integer.MAX_VALUE)
    public void execute_proposal_with_update_contract() throws Throwable {
        File nefFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_NEF_FILE.toString()).toURI());
        NefFile nef = NefFile.readFromFile(nefFile);

        File manifestFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_MANIFEST_FILE.toString()).toURI());
        ContractManifest manifest = getObjectMapper()
                .readValue(manifestFile, ContractManifest.class);
        byte[] manifestBytes = getObjectMapper().writeValueAsBytes(manifest);

        ContractParameter data = string("update contract");

        ContractParameter intents = array(array(treasury.getScriptHash(), UPDATE_CONTRACT,
                array(nef.toArray(), manifestBytes, data), CallFlags.ALL.getValue()));
        String offchainUri = "execute_proposal_with_update_contract";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("UpdatingContract"));
        assertThat(n.getContract(), is(treasury.getScriptHash()));
    }

}