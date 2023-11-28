package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import io.neow3j.contract.GasToken;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;

import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.CREATE;
import static com.axlabs.neo.grantshares.util.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.EVE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.FLORIAN;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.util.TestHelper.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.assertAborted;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class ProposalExecutionsTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static GrantSharesGovContract gov;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a DAO member.
    static Account eve; // Set to be a DAO member.
    static Account florian; // Set to be a DAO member.

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE), ext.getAccount(DENISE), ext.getAccount(EVE),
                ext.getAccount(FLORIAN)));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
        eve = ext.getAccount(EVE);
        florian = ext.getAccount(FLORIAN);
    }

    @Test
    public void fail_executing_non_existent_proposal() throws Throwable {
        Hash256 tx = gov.execute(1000).signers(AccountSigner.calledByEntry(alice)).sign().send()
                .getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal doesn't exist", neow3j);
    }

    @Test
    public void fail_executing_proposal_that_wasnt_endorsed() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 51), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_that_wasnt_endorsed";

        // 1. Create proposal then skip till after the queued phase without endorsing.
        Hash256 tx = gov.invokeFunction(CREATE, hash160(bob), intents, string(offchainUri),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH);
        int id = neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getStack().get(0).getInteger().intValue();

        // 2. Call execute
        tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal not in execution phase", neow3j);
    }

    @Test
    public void fail_executing_proposal_without_votes() throws Throwable {
        int newValue = 60;
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, newValue), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_without_votes";

        // 1. Create and endorse proposal, then skip till after the queued phase without voting.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH);

        // 2. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Quorum not reached", neow3j);
    }

    @Test
    public void fail_executing_accepted_proposal_multiple_times() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_accepted_proposal_multiple_times";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute the first time
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        assertThat(neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));

        // 4. Call execute the second time and fail.
        tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal already executed", neow3j);
    }

    @Test
    public void execute_proposal_with_multiple_intents() throws Throwable {
        ContractParameter intents = array(
                array(GasToken.SCRIPT_HASH, "transfer", array(alice.getScriptHash(),
                        bob.getScriptHash(), 1, any(null)), CallFlags.ALL.getValue()),
                array(GasToken.SCRIPT_HASH, "transfer", array(alice.getScriptHash(),
                        bob.getScriptHash(), 1, any(null)), CallFlags.ALL.getValue()));
        String offchainUri = "execute_proposal_with_multiple_intents";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice)
                        .setAllowedContracts(GasToken.SCRIPT_HASH))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        assertTrue(execution.getStack().get(0).getList().get(0).getBoolean());
        assertTrue(execution.getStack().get(0).getList().get(1).getBoolean());
        Notification n = execution.getNotifications().get(0);
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
        assertThat(n.getContract(), is(gov.getScriptHash()));
        assertThat(n.getState().getList().get(0).getInteger().intValue(), is(id));
    }

    @Test
    public void fail_executing_proposal_quorum_reached_but_rejected() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_quorum_reached_but_rejected";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, -1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        voteForProposal(gov, neow3j, id, 0, denise);
        voteForProposal(gov, neow3j, id, 0, florian);
        voteForProposal(gov, neow3j, id, 0, eve);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal rejected", neow3j);
    }

    @Test
    public void fail_executing_proposal_quorum_reached_but_all_abstained() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_quorum_reached_but_all_abstained";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote abstained for all members.
        voteForProposal(gov, neow3j, id, 0, alice);
        voteForProposal(gov, neow3j, id, 0, charlie);
        voteForProposal(gov, neow3j, id, 0, denise);
        voteForProposal(gov, neow3j, id, 0, eve);
        voteForProposal(gov, neow3j, id, 0, florian);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal rejected", neow3j);
    }

    @Test
    public void fail_executing_proposal_quorum_not_reached() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_quorum_not_reached";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, 1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Quorum not reached", neow3j);
    }

    @Test
    public void fail_executing_proposal_with_different_quorum_not_reached() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_with_different_quorum_not_reached";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri, 50, 75);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, 1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        voteForProposal(gov, neow3j, id, 1, eve);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Quorum not reached", neow3j);
    }

    @Test
    public void fail_executing_proposal_with_different_quorum_reached_different_rate_rejected() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "fail_executing_proposal_with_different_quorum_reached_different_rate_rejected";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri, 60, 75);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, 1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        voteForProposal(gov, neow3j, id, -1, eve);
        voteForProposal(gov, neow3j, id, -1, denise);
        voteForProposal(gov, neow3j, id, 0, florian);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal rejected", neow3j);
    }

    @Test
    public void succeed_executing_proposal_with_different_quorum_reached_different_rate_accepted() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, 40), CallFlags.ALL.getValue()));
        String offchainUri = "succeed_executing_proposal_with_different_quorum_reached_different_rate_accepted";

        // 1. Create and endorse proposal, then skip till voting phase.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri, 60, 75);
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 2. Vote such that the proposal is accepted.
        voteForProposal(gov, neow3j, id, 1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        voteForProposal(gov, neow3j, id, -1, eve);
        voteForProposal(gov, neow3j, id, 0, denise);
        voteForProposal(gov, neow3j, id, 0, florian);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 3. Call execute
        NeoVMStateType state = gov.invokeFunction(EXECUTE, integer(id)).signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getState();
        assertThat(state, is(NeoVMStateType.HALT));
    }

    @Test
    public void fail_executing_expired_proposal() throws Throwable {
        ContractParameter intents = array(array(gov.getScriptHash(), CHANGE_PARAM, array(REVIEW_LENGTH_KEY, 1),
                CallFlags.ALL.getValue()));
        String discUrl = "fail_executing_expired_proposal";

        // 1. Create and endorse proposal, then skip till after the queued phase without voting.
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, discUrl);
        ext.fastForwardOneBlock(PHASE_LENGTH); // skip review phase
        voteForProposal(gov, neow3j, id, 1, alice);
        voteForProposal(gov, neow3j, id, 1, charlie);
        voteForProposal(gov, neow3j, id, 1, denise);

        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH); // skip voting, time lock, expiration phase

        // 2. Call execute
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal expired", neow3j);
    }


}
