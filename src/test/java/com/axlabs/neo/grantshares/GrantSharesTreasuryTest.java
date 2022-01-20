package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
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
import java.util.Set;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.TestHelper.ADD_FUNDER;
import static com.axlabs.neo.grantshares.TestHelper.ADD_WHITELISTED_TOKEN;
import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.GET_FUNDERS;
import static com.axlabs.neo.grantshares.TestHelper.GET_WHITELISTED_TOKENS;
import static com.axlabs.neo.grantshares.TestHelper.IS_PAUSED;
import static com.axlabs.neo.grantshares.TestHelper.PAUSE;
import static com.axlabs.neo.grantshares.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.RELEASE_TOKENS;
import static com.axlabs.neo.grantshares.TestHelper.REMOVE_FUNDER;
import static com.axlabs.neo.grantshares.TestHelper.REMOVE_WHITELISTED_TOKEN;
import static com.axlabs.neo.grantshares.TestHelper.UNPAUSE;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
import static io.neow3j.types.ContractParameter.publicKey;
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

    private static final int NEO_MAX_AMOUNT = 100;
    private static final int GAS_MAX_AMOUNT = 10000;
    private static final int GAS_MAX_NEW_AMOUNT = 111111;
    private static final Hash160 FLM_TOKEN =
            new Hash160("0xf0151f528127558851b39c2cd8aa47da7418ab28");
    private static final int FLM_MAX_AMOUNT = 10000;

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

        config.setDeployParam(array(gov.getScriptHash(), funders, tokensParam));
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
    }

    @Test
    @Order(0)
    public void getFunders() throws IOException {
        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();
        assertThat(funders.size(), is(2));
        List<byte[]> pubKeys = funders.stream()
                .map(StackItem::getByteArray)
                .collect(Collectors.toList());
        assertThat(pubKeys, containsInAnyOrder(bob.getECKeyPair().getPublicKey().getEncoded(true),
                denise.getECKeyPair().getPublicKey().getEncoded(true)));
    }

    @Test
    @Order(0)
    public void getWhitelistedTokens() throws IOException {
        Map<StackItem, StackItem> tokens = treasury.callInvokeFunction(GET_WHITELISTED_TOKENS)
                .getInvocationResult().getStack().get(0).getMap();
        assertThat(tokens.size(), is(2));
        Set<String> tokenAddresses = tokens.keySet().stream()
                .map(StackItem::getAddress).collect(Collectors.toSet());
        assertThat(tokenAddresses, containsInAnyOrder(NeoToken.SCRIPT_HASH.toAddress(),
                GasToken.SCRIPT_HASH.toAddress()));

        Set<Integer> tokenMaxes = tokens.values().stream()
                .map(si -> si.getInteger().intValue()).collect(Collectors.toSet());
        assertThat(tokenMaxes, containsInAnyOrder(NEO_MAX_AMOUNT, GAS_MAX_AMOUNT));
    }

    @Test
    @Order(1)
    public void execute_proposal_with_add_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_FUNDER,
                        array(publicKey(alice.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "execute_proposal_with_add_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, charlie, intents, desc);

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
        assertThat(n.getEventName(), is("FunderAdded"));

        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();

        assertThat(funders.size(), is(3));
    }

    @Test
    @Order(2)
    public void fail_adding_already_added_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_FUNDER,
                        array(publicKey(ext.getAccount(DENISE).getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "fail_adding_already_added_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Already a funder"));
    }

    @Test
    @Order(3)
    public void execute_proposal_with_remove_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), REMOVE_FUNDER, array(publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "execute_proposal_with_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("FunderRemoved"));

        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();

        assertThat(funders.size(), is(2));
    }

    @Test
    @Order(4)
    public void fail_removing_nonexistant_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), REMOVE_FUNDER,
                        array(publicKey(charlie.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "fail_execution_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not a funder"));
    }

    @Test
    @Order(5)
    public void execute_proposal_with_remove_whitelisted_token() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), REMOVE_WHITELISTED_TOKEN,
                        array(NeoToken.SCRIPT_HASH)));
        String desc = "execute_proposal_with_remove_whitelisted_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenRemoved"));

        Map<StackItem, StackItem> tokens = treasury.callInvokeFunction(GET_WHITELISTED_TOKENS)
                .getInvocationResult().getStack().get(0).getMap();
        assertThat(tokens.size(), is(1));
    }

    @Test
    @Order(6)
    public void execute_proposal_with_add_whitelisted_token() throws Throwable {

        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_WHITELISTED_TOKEN, array(FLM_TOKEN,
                        FLM_MAX_AMOUNT)));
        String desc = "execute_proposal_with_add_whitelisted_token";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenAdded"));

        Map<StackItem, StackItem> tokens = treasury.callInvokeFunction(GET_WHITELISTED_TOKENS)
                .getInvocationResult().getStack().get(0).getMap();
        assertThat(tokens.size(), is(2));
        Set<String> tokenAddresses = tokens.keySet().stream()
                .map(StackItem::getAddress).collect(Collectors.toSet());
        assertThat(tokenAddresses, containsInAnyOrder(FLM_TOKEN.toAddress(),
                GasToken.SCRIPT_HASH.toAddress()));
    }

    @Test
    @Order(7)
    public void execute_proposal_with_change_whitelisted_token_max_amount() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_WHITELISTED_TOKEN, array(GasToken.SCRIPT_HASH
                        , GAS_MAX_NEW_AMOUNT)));
        String desc = "execute_proposal_with_change_whitelisted_token_max_amount";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("WhitelistedTokenAdded"));

        Map<StackItem, StackItem> tokens = treasury.callInvokeFunction(GET_WHITELISTED_TOKENS)
                .getInvocationResult().getStack().get(0).getMap();
        assertThat(tokens.size(), is(2));
        Set<String> tokenAddresses = tokens.keySet().stream()
                .map(StackItem::getAddress).collect(Collectors.toSet());
        assertThat(tokenAddresses, containsInAnyOrder(GasToken.SCRIPT_HASH.toAddress(),
                FLM_TOKEN.toAddress()));

        Set<Integer> tokenMaxes = tokens.values().stream()
                .map(si -> si.getInteger().intValue()).collect(Collectors.toSet());
        assertThat(tokenMaxes, containsInAnyOrder(GAS_MAX_NEW_AMOUNT, FLM_MAX_AMOUNT));
    }

    @Test
    public void fail_calling_add_whitelisted_token_directly() throws IOException {
        String exception =
                treasury.invokeFunction(ADD_WHITELISTED_TOKEN,
                                hash160(GasToken.SCRIPT_HASH),
                                ContractParameter.integer(1))
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void fail_calling_remove_whitelisted_token_directly() throws IOException {
        String exception =
                treasury.invokeFunction(REMOVE_WHITELISTED_TOKEN,
                                hash160(GasToken.SCRIPT_HASH))
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void fail_calling_add_funder_directly() throws Exception {
        String exception = treasury.invokeFunction(ADD_FUNDER, ContractParameter.publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void fail_calling_remove_funder_directly() throws Exception {
        String exception = treasury.invokeFunction(REMOVE_FUNDER, ContractParameter.publicKey(
                        charlie.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void fail_funding_treasury_with_non_funder() throws Throwable {
        String exception =
                new GasToken(neow3j).transfer(alice, treasury.getScriptHash(),
                                BigInteger.valueOf(10))
                        .signers(AccountSigner.calledByEntry(alice))
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Non-whitelisted sender"));
    }

    @Test
    @Order(8)
    public void fail_funding_treasury_with_non_whitelisted_token() throws Throwable {
        String exception =
                new NeoToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(10))
                        .signers(AccountSigner.calledByEntry(bob))
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Non-whitelisted token"));
    }

    @Test
    public void funding_treasury_with_whitelisted_token_and_funder() throws Throwable {
        Hash256 tx = new GasToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.ONE)
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
    }

    @Order(101)
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

    @Order(102)
    @Test
    public void fail_add_funder_on_paused_contract() throws Throwable {
        String exception = treasury.invokeFunction(ADD_FUNDER, ContractParameter.publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(102)
    @Test
    public void fail_remove_funder_on_paused_contract() throws Throwable {
        String exception = treasury.invokeFunction(REMOVE_FUNDER, ContractParameter.publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(103)
    @Test
    public void fail_add_whitelisted_token_on_paused_contract() throws Throwable {
        String exception = treasury.invokeFunction(ADD_WHITELISTED_TOKEN,
                        hash160(GasToken.SCRIPT_HASH),
                        ContractParameter.integer(1))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(104)
    @Test
    public void fail_remove_whitelisted_token_on_paused_contract() throws Throwable {
        String exception = treasury.invokeFunction(REMOVE_WHITELISTED_TOKEN,
                        hash160(GasToken.SCRIPT_HASH))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(105)
    @Test
    public void fail_release_tokens_on_paused_contract() throws Throwable {
        String exception = treasury.invokeFunction(RELEASE_TOKENS,
                        hash160(GasToken.SCRIPT_HASH),
                        hash160(alice.getScriptHash()),
                        integer(1))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(106)
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

    // TODO: Test
    //   - Release tokens
    //   - update contract
}