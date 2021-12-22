package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.stackitem.ByteStringStackItem;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.CREATE;
import static com.axlabs.neo.grantshares.TestHelper.ENDORSE;
import static com.axlabs.neo.grantshares.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.GET_PROPOSAL;
import static com.axlabs.neo.grantshares.TestHelper.GET_PROPOSALS;
import static com.axlabs.neo.grantshares.TestHelper.GET_PROPOSAL_COUNT;
import static com.axlabs.neo.grantshares.TestHelper.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.TestHelper.MIN_QUORUM;
import static com.axlabs.neo.grantshares.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.PROPOSAL_CREATED;
import static com.axlabs.neo.grantshares.TestHelper.PROPOSAL_ENDORSED;
import static com.axlabs.neo.grantshares.TestHelper.VOTE;
import static com.axlabs.neo.grantshares.TestHelper.VOTED;
import static com.axlabs.neo.grantshares.TestHelper.createSimpleProposal;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class GrantSharesGovTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract contract;
    private static Account alice; // Set to be a DAO member.
    private static Account bob;
    private static Account charlie; // Set to be a DAO member.
    private static int defaultProposalId;

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

        Hash256 creationTx = contract.invokeFunction(CREATE, hash160(alice.getScriptHash()),
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                                array(new Hash160(defaultAccountScriptHash())))),
                        string("default_proposal"),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        defaultProposalId = neow3j.getApplicationLog(creationTx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getInteger().intValue();
    }

    @Test
    public void succeed_creating_and_retrieving_proposal() throws Throwable {
        // 1. Setup and create proposal
        Hash160 targetContract = NeoToken.SCRIPT_HASH;
        String targetMethod = "transfer";
        Hash160 targetParam1 = contract.getScriptHash();
        Hash160 targetParam2 = alice.getScriptHash();
        int targetParam3 = 1;
        ContractParameter intent = array(targetContract, targetMethod,
                array(targetParam1, targetParam2, targetParam3));
        String proposalDescription = "description of the proposal";
        Hash256 proposalCreationTx = contract.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(proposalDescription),
                        integer(-1)) // no linked proposal
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);

        int id = neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Test correct setup of the created proposal.
        NeoInvokeFunction r = contract.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        TestHelper.Proposal p =
                TestHelper.convert(r.getInvocationResult().getStack().get(0).getList());
        assertThat(p.id, is(id));
        assertThat(p.proposer, is(alice.getScriptHash()));
        assertThat(p.linkedProposal, is(-1));
        assertThat(p.acceptanceRate, is(MIN_ACCEPTANCE_RATE));
        assertThat(p.quorum, is(MIN_QUORUM));
        assertThat(p.intents.size(), is(1));
        assertThat(p.intents.get(0).targetContract, is(targetContract));
        assertThat(p.intents.get(0).method, is(targetMethod));
        assertThat(p.intents.get(0).params.get(0).getAddress(), is(targetParam1.toAddress()));
        assertThat(p.intents.get(0).params.get(1).getAddress(), is(targetParam2.toAddress()));
        assertThat(p.intents.get(0).params.get(2).getInteger().intValue(), is(targetParam3));

        // 3. Test CreateProposal event values
        List<NeoApplicationLog.Execution.Notification> ntfs =
                neow3j.getApplicationLog(proposalCreationTx).send().getApplicationLog()
                        .getExecutions().get(0).getNotifications();

        NeoApplicationLog.Execution.Notification ntf = ntfs.get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_CREATED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getInteger().intValue(), is(id));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
        assertThat(state.get(2).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(state.get(3).getInteger().intValue(), is(MIN_QUORUM));
    }

    @Test
    public void fail_creating_with_missing_linked_proposal() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(contract.getScriptHash(), alice.getScriptHash(), 1));
        String description = "fail_creating_with_missing_linked_proposal";
        byte[] linkedProposal = Hash256.ZERO.toArray();

        String exception = contract.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(description),
                        byteArray(linkedProposal))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Linked proposal doesn't exist"));
    }

    @Test
    public void fail_creating_with_bad_quorum() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(contract.getScriptHash(), alice.getScriptHash(), 1));
        String desc = "fail_creating_with_bad_quorum";

        String exception = contract.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(desc),
                        any(null), // linked proposal
                        integer(MIN_ACCEPTANCE_RATE),
                        integer(MIN_QUORUM - 1))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();

        assertThat(exception, containsString("Quorum not allowed"));
    }

    @Test
    public void fail_creating_with_bad_acceptance_rate() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(contract.getScriptHash(), alice.getScriptHash(), 1));
        String desc = "fail_creating_with_bad_acceptance_rate";

        String exception = contract.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(desc),
                        any(null), // linked proposal
                        integer(MIN_ACCEPTANCE_RATE - 1),
                        integer(MIN_QUORUM))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();

        assertThat(exception, containsString("Acceptance rate not allowed"));
    }

    @Test
    public void succeed_endorsing_with_member() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = createSimpleProposal(contract, alice, "succeed_endorsing_with_member");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Test that proposal endorser and phases have not yet been setup.
        List<StackItem> proposal = contract.callInvokeFunction(GET_PROPOSAL,
                asList(integer(id))).getInvocationResult().getStack().get(0).getList();
        assertThat(proposal.get(5).getValue(), is(nullValue()));
        assertThat(proposal.get(6).getInteger(), is(BigInteger.ZERO));
        assertThat(proposal.get(7).getInteger(), is(BigInteger.ZERO));
        assertThat(proposal.get(8).getInteger(), is(BigInteger.ZERO));

        // 3. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 4. Test the right setup of the proposal phases
        NeoInvokeFunction r = contract.callInvokeFunction(GET_PROPOSAL,
                asList(integer(id)));
        proposal = r.getInvocationResult().getStack().get(0).getList();
        assertThat(proposal.get(5).getAddress(), is(alice.getAddress()));
        int n = neow3j.getTransactionHeight(endorseTx).send().getHeight().intValue();
//        long time = neow3j.getBlock(BigInteger.valueOf(n), false).send().getBlock().getTime();
        assertThat(proposal.get(6).getInteger().intValue(), is(n + PHASE_LENGTH));
        assertThat(proposal.get(7).getInteger().intValue(), is(n + PHASE_LENGTH + PHASE_LENGTH));
        assertThat(proposal.get(8).getInteger().intValue(), is(n + PHASE_LENGTH + PHASE_LENGTH
                + PHASE_LENGTH));

        // 5. Test the right setup of the votes map
        r = contract.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        List<StackItem> votes = r.getInvocationResult().getStack().get(0).getList();
        assertThat(votes.get(12).getInteger(), is(BigInteger.ZERO));
        assertThat(votes.get(13).getInteger(), is(BigInteger.ZERO));
        assertThat(votes.get(14).getInteger(), is(BigInteger.ZERO));

        // 6. Test emitted "endorsed" event
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(endorseTx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_ENDORSED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getInteger().intValue(), is(id));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
    }

    @Test
    public void fail_endorsing_with_non_member() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        integer(defaultProposalId), hash160(bob.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    public void fail_endorsing_with_member_but_wrong_signer() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        integer(defaultProposalId), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    public void fail_endorsing_already_endorsed_proposal() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = createSimpleProposal(contract, alice,
                "fail_endorsing_already_endrosed");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Endorse again
        String exception = contract.callInvokeFunction(ENDORSE, asList(integer(id),
                        hash160(charlie.getScriptHash())), AccountSigner.calledByEntry(charlie))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Proposal already endorsed"));
    }

    @Test
    public void fail_endorsing_non_existent_proposal() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        byteArray(Hash256.ZERO.toArray()), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(alice)).getInvocationResult();
        assertThat(res.getException(), containsString("Proposal doesn't exist"));
    }

    @Test
    public void succeed_voting() throws Throwable {
        // 1. Create proposal
        Hash256 creationTx = createSimpleProposal(contract, bob, "succeed_voting");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Wait till review phase ends.
        ext.fastForward(PHASE_LENGTH);

        // 4. Vote
        Hash256 voteTx = contract.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Test the right setting of the votes
        NeoInvokeFunction r = contract.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        List<StackItem> votesStruct = r.getInvocationResult().getStack().get(0).getList();
        assertThat(votesStruct.get(12).getInteger(), is(BigInteger.ZERO));
        assertThat(votesStruct.get(13).getInteger(), is(BigInteger.ONE));
        assertThat(votesStruct.get(14).getInteger(), is(BigInteger.ZERO));
        Map<StackItem, StackItem> votes = votesStruct.get(15).getMap();
        assertThat(votes.size(), is(1));
        assertThat(votes.get(new ByteStringStackItem(charlie.getScriptHash().toLittleEndianArray()))
                .getInteger().intValue(), is(-1));

        // 6. Test the emitted vote event
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(voteTx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(VOTED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getInteger().intValue(), is(id));
        assertThat(state.get(1).getAddress(), is(charlie.getAddress()));
        assertThat(state.get(2).getInteger().intValue(), is(-1));
    }

    @Test
    public void fail_voting_in_review_and_queued_phase() throws Throwable {
        Hash256 creationTx = createSimpleProposal(contract, bob, "fail_voting_in_review_phase");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send().getApplicationLog().getExecutions()
                .get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Vote in review phase
        String exception = contract.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));

        // 5. Fast-forward till after the voting phase.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);

        // 4. Vote in queued or later phase
        exception = contract.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));
    }

    @Test
    public void fail_voting_multiple_times() throws Throwable {
        Hash256 creationTx = createSimpleProposal(contract, bob, "fail_voting_multiple_times");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Fast-forward to the voting phase.
        ext.fastForward(PHASE_LENGTH);

        // 4. Vote the first time
        Hash256 voteTx = contract.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Vote the second time
        String exception = contract.invokeFunction(VOTE, integer(id), integer(1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Already voted on this proposal"));

        // 6. Check votes
        NeoInvokeFunction r =
                contract.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        List<StackItem> votesStruct = r.getInvocationResult().getStack().get(0).getList();
        assertThat(votesStruct.get(12).getInteger(), is(BigInteger.ZERO));
        assertThat(votesStruct.get(13).getInteger(), is(BigInteger.ONE));
        assertThat(votesStruct.get(14).getInteger(), is(BigInteger.ZERO));
        Map<StackItem, StackItem> votes = votesStruct.get(15).getMap();
        assertThat(votes.size(), is(1));
        assertThat(votes.get(new ByteStringStackItem(charlie.getScriptHash().toLittleEndianArray()))
                .getInteger().intValue(), is(-1));
    }

    @Test
    public void fail_voting_with_non_member() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = contract.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(-1), hash160(bob.getScriptHash()))
                .signers(AccountSigner.calledByEntry(bob)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    public void fail_voting_on_non_existent_proposal() throws IOException {
        String exception = contract.invokeFunction(VOTE, byteArray("0102030405"),
                        integer(-1), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Proposal doesn't exist"));
    }

    @Test
    public void fail_voting_on_not_enorsed_proposal() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = contract.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(-1), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Proposal wasn't endorsed"));
    }

    @Test
    public void fail_voting_with_invalid_vote() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = contract.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(2), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Invalid vote"));
    }

    @Test
    public void create_proposal_with_large_intents_and_description() throws Throwable {
        String desc =
                "aabcababcababcababcabbcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcaabcabcabcabcabcabca";
        Hash256 tx = contract.invokeFunction(CREATE, hash160(bob),
                        array(
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))),
                                array(NeoToken.SCRIPT_HASH, "jjsldfjklkasjdfkljalkjasdf;lkjasddfd" +
                                                "lfjkasdflkjasdfssldfjklkasjdfkljasasdfasfasdfasdf",
                                        array(
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash())
                                        )),
                                array(NeoToken.SCRIPT_HASH, "jjsldfjklkasjdfkljalkjasdf;lkjasddfd" +
                                                "lfjkasdflkjasdfssldfjklkasjdfkljasasdfasfasdfasdf",
                                        array(
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash())
                                        )),
                                array(NeoToken.SCRIPT_HASH, "jjsldfjklkasjdfkljalkjasdf;lkjasddfd" +
                                                "lfjkasdflkjasdfssldfjklkasjdfkljasasdfasfasdfasdf",
                                        array(
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfiojpasodm" +
                                                        "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfi" +
                                                        "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfi" +
                                                        "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfi" +
                                                        "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfi" +
                                                        "hlksajdfiojasdofjasodjflkjasdkfjlaijsdfi"
                                        ))
                        ),
                        string(desc),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        int id = neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getStack().get(0).getInteger().intValue();

        NeoInvokeFunction r = contract.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        TestHelper.Proposal p =
                TestHelper.convert(r.getInvocationResult().getStack().get(0).getList());
        assertThat(p.id, is(id));
        assertThat(p.proposer, is(bob.getScriptHash()));
        assertThat(p.linkedProposal, is(-1));
        assertThat(p.acceptanceRate, is(MIN_ACCEPTANCE_RATE));
        assertThat(p.quorum, is(MIN_QUORUM));
        assertThat(p.intents.size(), is(9));
        assertThat(p.desc, is(desc));
    }

    @Test
    public void succeed_creating_exact_same_proposal_that_already_exists() throws IOException {
        // Recreate default proposal
        InvocationResult result = contract.invokeFunction(CREATE, hash160(alice.getScriptHash()),
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                                array(new Hash160(defaultAccountScriptHash())))),
                        string("default_proposal"),
                        integer(-1)).signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult();
        assertThat(result.getStack().get(0).getInteger().intValue(), is(not(defaultProposalId)));
        assertThat(result.getStack().get(0).getInteger().intValue(), is(greaterThan(0)));
    }

    @Test
    public void get_proposals() throws Throwable {
        ContractParameter intents = array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                array(new Hash160(defaultAccountScriptHash()))));
        TestHelper.createAndEndorseProposal(contract, neow3j, bob, alice, intents, "some_proposal");

        List<StackItem> page = contract.callInvokeFunction(GET_PROPOSALS,
                        asList(integer(0), integer(1))) // get page 0 with page size 1
                .getInvocationResult().getStack().get(0).getList();
        assertThat(page.get(0).getInteger().intValue(), is(0));
        assertThat(page.get(1).getInteger().intValue(), is(greaterThanOrEqualTo(2))); // at least
        // two pages
        assertThat(page.get(2).getList().size(), is(1)); // the list of proposals should be 1
        TestHelper.Proposal prop = TestHelper.convert(page.get(2).getList().get(0).getList());
        assertThat(prop.id, is(defaultProposalId));

        prop = TestHelper.convert(contract.callInvokeFunction(GET_PROPOSALS,
                        asList(integer(1), integer(1))) // get page 1 with page size 1
                .getInvocationResult().getStack().get(0).getList().get(2).getList().get(0).getList());
        assertThat(prop.id, is(greaterThanOrEqualTo(1)));
    }

    @Test
    public void get_number_of_proposals() throws IOException {
        assertThat(contract.callInvokeFunction(GET_PROPOSAL_COUNT)
                        .getInvocationResult().getStack().get(0).getInteger().intValue(),
                is(greaterThanOrEqualTo(1)));
        // At least one because of the default proposal from the setup method.
    }


    // TODO:
    //  update gov contract
    //  fail calling update method directly
}