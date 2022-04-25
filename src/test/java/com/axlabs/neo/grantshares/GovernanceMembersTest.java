package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.util.TestHelper.ADD_MEMBER;
import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CALC_MEMBER_MULTI_SIG_ACC;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_MEMBERS;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_MEMBERS_COUNT;
import static com.axlabs.neo.grantshares.util.TestHelper.MEMBER_ADDED;
import static com.axlabs.neo.grantshares.util.TestHelper.MEMBER_REMOVED;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.util.TestHelper.REMOVE_MEMBER;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GovernanceMembersTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static GrantSharesGovContract contract;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a DAO member.

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE), ext.getAccount(DENISE)));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
    }

    //region ADD MEMBER
    @Test
    public void fail_calling_add_member_directly() throws IOException {
        String exception = contract.callInvokeFunction(ADD_MEMBER,
                asList(hash160(bob)),
                AccountSigner.calledByEntry(alice)).getInvocationResult().getException();

        assertThat(exception, containsString("Method only callable by the contract itself"));
    }

    @Order(1) // Is executed before the execute_remove_member test which removes bob from members.
    @Test
    public void execute_add_member() throws Throwable {
        assertThat(contract.getMembersCount(), is(3));
        List<ECKeyPair.ECPublicKey> initMembers = contract.getMembers();

        ContractParameter intents = array(array(
                contract.getScriptHash(),
                ADD_MEMBER,
                array(publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true))),
                CallFlags.ALL.getValue()));
        String offchainUri = "execute_add_member";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(contract, neow3j, id, alice);
        voteForProposal(contract, neow3j, id, charlie);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = contract.execute(id).signers(AccountSigner.calledByEntry(charlie)).sign().send()
                .getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        List<StackItem> returnVals = execution.getStack().get(0).getList();
        assertThat(returnVals.size(), is(1));
        assertThat(returnVals.get(0).getValue(), is(nullValue()));
        assertThat(execution.getNotifications().get(0).getEventName(), is(MEMBER_ADDED));
        assertThat(execution.getNotifications().get(0).getState().getList().get(0).getAddress(),
                is(bob.getAddress()));
        assertThat(execution.getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));

        List<ECKeyPair.ECPublicKey> newMembers = contract.getMembers();
        assertThat(newMembers.size(), is(initMembers.size() + 1));
        assertThat(newMembers, containsInAnyOrder(
                bob.getECKeyPair().getPublicKey(),
                charlie.getECKeyPair().getPublicKey(),
                alice.getECKeyPair().getPublicKey(),
                denise.getECKeyPair().getPublicKey()));

        assertThat(contract.getMembersCount(), is(4));
    }

    @Test
    public void fail_execute_add_member_with_already_member() throws Throwable {
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                ADD_MEMBER,
                array(publicKey(alice.getECKeyPair().getPublicKey().getEncoded(true))),
                CallFlags.ALL.getValue()));
        String offchainUri = "fail_execute_add_member_with_already_member";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, offchainUri);
        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(contract, neow3j, id, alice);
        voteForProposal(contract, neow3j, id, charlie);
        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = contract.execute(id).signers(AccountSigner.calledByEntry(charlie)).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Already a member"));
    }

    @Test
    public void fail_execute_add_member_with_invalid_public_key() throws Throwable {
        // first byte is '04' instead of '03'
        String invalidPubKey = "043a4d051b04b7fc0230d2b1aaedfd5a84be279a5361a7358db665ad7857787f1b";
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                ADD_MEMBER,
                array(publicKey(invalidPubKey)),
                CallFlags.ALL.getValue()));
        String offchainUri = "fail_execute_add_member_with_invalid_public_key";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, offchainUri);
        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(contract, neow3j, id, alice);
        voteForProposal(contract, neow3j, id, charlie);
        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = contract.execute(id).signers(AccountSigner.calledByEntry(charlie)).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Incorrect length"));
    }
    //endregion ADD MEMBER

    //region REMOVE MEMBER
    @Test
    public void fail_calling_remove_member_directly() throws IOException {
        String exception = contract.callInvokeFunction(REMOVE_MEMBER,
                asList(hash160(bob)),
                AccountSigner.calledByEntry(alice)).getInvocationResult().getException();

        assertThat(exception, containsString("Method only callable by the contract itself"));
    }

    @Order(2) // Is executed right after the execute_add_member test to remove bob from members
    @Test
    public void execute_remove_member() throws Throwable {
        List<ECKeyPair.ECPublicKey> initMembers = contract.getMembers();

        ContractParameter intents = array(array(
                contract.getScriptHash(),
                REMOVE_MEMBER,
                array(publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true))),
                CallFlags.ALL.getValue()));
        String offchainUri = "execute_remove_member";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(contract, neow3j, id, alice);
        voteForProposal(contract, neow3j, id, charlie);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = contract.execute(id).signers(AccountSigner.calledByEntry(charlie)).sign().send()
                .getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        List<StackItem> returnVals = execution.getStack().get(0).getList();
        assertThat(returnVals.size(), is(1));
        assertThat(returnVals.get(0).getValue(), is(nullValue()));
        assertThat(execution.getNotifications().get(0).getEventName(), is(MEMBER_REMOVED));
        assertThat(execution.getNotifications().get(0).getState().getList().get(0).getAddress(),
                is(bob.getAddress()));
        assertThat(execution.getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));

        List<ECKeyPair.ECPublicKey> newMembers = contract.getMembers();
        assertThat(newMembers.size(), is(initMembers.size() - 1));
        assertThat(newMembers, containsInAnyOrder(
                charlie.getECKeyPair().getPublicKey(),
                alice.getECKeyPair().getPublicKey(),
                denise.getECKeyPair().getPublicKey()));
    }

    @Test
    public void fail_execute_remove_member_with_non_member() throws Throwable {
        Account acc = Account.create();
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                REMOVE_MEMBER,
                array(publicKey(acc.getECKeyPair().getPublicKey().getEncoded(true))),
                CallFlags.ALL.getValue()));
        String offchainUri = "fail_execute_remove_member_with_non_member";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, offchainUri);
        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(contract, neow3j, id, alice);
        voteForProposal(contract, neow3j, id, charlie);
        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = contract.execute(id).signers(AccountSigner.calledByEntry(charlie)).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Not a member"));
    }

    //endregion REMOVE MEMBER

    @Test
    public void get_members() throws IOException {
        List<byte[]> members = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getByteArray).collect(Collectors.toList());
        assertThat(members, contains(
                alice.getECKeyPair().getPublicKey().getEncoded(true),
                charlie.getECKeyPair().getPublicKey().getEncoded(true),
                denise.getECKeyPair().getPublicKey().getEncoded(true)));
    }

    @Test
    public void calc_members_multisig_account() throws IOException {
        Account membersAccount = createMultiSigAccount(2, alice, charlie, denise);
        assertThat(contract.callInvokeFunction(CALC_MEMBER_MULTI_SIG_ACC).getInvocationResult()
                .getStack().get(0).getAddress(), is(membersAccount.getAddress()));
    }

}
