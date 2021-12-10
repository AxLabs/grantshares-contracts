package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
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

import static com.axlabs.neo.grantshares.TestHelper.ADD_MEMBER;
import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.GET_MEMBERS;
import static com.axlabs.neo.grantshares.TestHelper.MEMBER_ADDED;
import static com.axlabs.neo.grantshares.TestHelper.MEMBER_REMOVED;
import static com.axlabs.neo.grantshares.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.TestHelper.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.REMOVE_MEMBER;
import static com.axlabs.neo.grantshares.TestHelper.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.hasher;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
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
    static SmartContract contract;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.

    @DeployConfig(GrantSharesGov.class)
    public static void deployConfig(DeployConfiguration config) throws Exception {
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE).getScriptHash(),
                ext.getAccount(CHARLIE).getScriptHash()));
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(GrantSharesGov.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
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
        List<String> initMembers = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getAddress).collect(Collectors.toList());

        ContractParameter intents = array(array(
                contract.getScriptHash(),
                ADD_MEMBER,
                array(bob.getScriptHash())));
        String desc = "execute_add_member";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, proposalHash, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        Hash256 tx = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
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

        List<String> newMembers = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getAddress).collect(Collectors.toList());
        assertThat(newMembers.size(), is(initMembers.size() + 1));
        assertThat(newMembers, containsInAnyOrder(bob.getAddress(), charlie.getAddress(),
                alice.getAddress()));
    }

    @Test
    public void fail_execute_add_member_with_already_member() throws Throwable {
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                ADD_MEMBER,
                array(alice.getScriptHash())));
        String desc = "fail_execute_add_member_with_already_member";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);
        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, proposalHash, alice);
        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        String exception = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesGov: Already a member"));
    }

    // TODO: execute add member with invalid script hash
    //region ADD MEMBER

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
        List<String> initMembers = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getAddress).collect(Collectors.toList());

        ContractParameter intents = array(array(
                contract.getScriptHash(),
                REMOVE_MEMBER,
                array(bob.getScriptHash())));
        String desc = "execute_remove_member";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, proposalHash, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        Hash256 tx = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
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

        List<String> newMembers = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getAddress).collect(Collectors.toList());
        assertThat(newMembers.size(), is(initMembers.size() - 1));
        assertThat(newMembers, containsInAnyOrder(charlie.getAddress(), alice.getAddress()));
    }

    @Test
    public void fail_execute_remove_member_with_non_member() throws Throwable {
        Account acc = Account.create();
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                REMOVE_MEMBER,
                array(acc.getScriptHash())));
        String desc = "fail_execute_remove_member_with_non_member";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);
        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, proposalHash, alice);
        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        String exception = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesGov: Not a member"));
    }

    // TODO: execute remove member with invalid script hash

    //region REMOVE MEMBER

    @Test
    public void get_members() throws IOException {
        List<String> members = contract.callInvokeFunction(GET_MEMBERS).getInvocationResult()
                .getStack().get(0).getList().stream()
                .map(StackItem::getAddress).collect(Collectors.toList());
        assertThat(members, containsInAnyOrder(alice.getAddress(), charlie.getAddress()));
    }


}
