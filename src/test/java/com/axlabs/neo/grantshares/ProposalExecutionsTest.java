package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.math.BigInteger;

import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.CREATE;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.TestHelper.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class ProposalExecutionsTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static SmartContract contract;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE).getScriptHash(),
                ext.getAccount(CHARLIE).getScriptHash()));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(GrantSharesGov.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
    }

    @Test
    public void fail_executing_non_existent_proposal() throws IOException {
        String exception = contract.callInvokeFunction(EXECUTE, asList(integer(1000)))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Proposal doesn't exist"));
    }

    @Test
    public void fail_executing_proposal_that_wasnt_endorsed() throws Throwable {
        ContractParameter intents = array(array(contract.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 51)));
        String desc = "fail_executing_proposal_that_wasnt_endorsed";

        // 1. Create proposal then skip till after the queued phase without endorsing.
        Hash256 tx = contract.invokeFunction(CREATE, hash160(bob), intents, string(desc),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        ext.fastForward(REVIEW_LENGTH + VOTING_LENGTH + QUEUED_LENGTH);
        int id = neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getStack().get(0).getInteger().intValue();

        // 2. Call execute
        String exception = contract.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal wasn't endorsed yet"));
    }

    @Test
    public void fail_executing_proposal_without_votes() throws Throwable {
        int newValue = 60;
        ContractParameter intents = array(array(contract.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, newValue)));
        String desc = "fail_executing_proposal_without_votes";

        // 1. Create and endorse proposal, then skip till after the queued phase without voting.
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);
        ext.fastForward(REVIEW_LENGTH + VOTING_LENGTH + QUEUED_LENGTH);

        // 2. Call execute
        String exception = contract.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Quorum not reached"));
    }

    @Test
    public void fail_executing_accepted_proposal_multiple_times() throws Throwable {
        ContractParameter intents = array(array(contract.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40)));
        String desc = "fail_executing_accepted_proposal_multiple_times";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);
        ext.fastForward(REVIEW_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(contract, neow3j, id, alice);
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);

        // 3. Call execute the first time
        Hash256 tx = contract.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        assertThat(neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));

        // 4. Call execute the second time and fail.
        String exception = contract.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal already executed"));
    }


    // TODO:
    //  - succeed voting and executing proposal that has different quorum and acceptance rate.
    //  - succeed executing proposal that has multiple intents.

    @Test
    public void execute_proposal_with_multiple_intents() throws Throwable {
        ContractParameter intents = array(
                array(GasToken.SCRIPT_HASH, "transfer", array(alice.getScriptHash(),
                        bob.getScriptHash(), 1, any(null))),
                array(GasToken.SCRIPT_HASH, "transfer", array(alice.getScriptHash(),
                        bob.getScriptHash(), 1, any(null))));
        String desc = "execute_proposal_with_multiple_intents";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        Hash256 tx = contract.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice)
                        .setAllowedContracts(GasToken.SCRIPT_HASH))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        assertTrue(execution.getStack().get(0).getList().get(0).getBoolean());
        assertTrue(execution.getStack().get(0).getList().get(1).getBoolean());
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("Transfer"));
        assertThat(n.getContract(), is(GasToken.SCRIPT_HASH));
        assertThat(n.getState().getList().get(0).getAddress(), is(alice.getAddress()));
        assertThat(n.getState().getList().get(1).getAddress(), is(bob.getAddress()));
        assertThat(n.getState().getList().get(2).getInteger(), is(BigInteger.ONE));
        n = execution.getNotifications().get(1);
        assertThat(n.getEventName(), is("Transfer"));
        assertThat(n.getContract(), is(GasToken.SCRIPT_HASH));
        assertThat(n.getState().getList().get(0).getAddress(), is(alice.getAddress()));
        assertThat(n.getState().getList().get(1).getAddress(), is(bob.getAddress()));
        assertThat(n.getState().getList().get(2).getInteger(), is(BigInteger.ONE));
        n = execution.getNotifications().get(2);
        assertThat(n.getEventName(), is(PROPOSAL_EXECUTED));
        assertThat(n.getContract(), is(contract.getScriptHash()));
        assertThat(n.getState().getList().get(0).getInteger().intValue(), is(id));
    }

}
