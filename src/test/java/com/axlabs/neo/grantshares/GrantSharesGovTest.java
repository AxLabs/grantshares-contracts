package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import com.axlabs.neo.grantshares.util.ProposalStruct;
import com.axlabs.neo.grantshares.util.TestHelper;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.NeoToken;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
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
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
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
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.util.TestHelper.ADD_MEMBER;
import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.CREATE;
import static com.axlabs.neo.grantshares.util.TestHelper.ENDORSE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_PROPOSAL;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_PROPOSALS;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_PROPOSAL_COUNT;
import static com.axlabs.neo.grantshares.util.TestHelper.IS_PAUSED;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_QUORUM;
import static com.axlabs.neo.grantshares.util.TestHelper.PAUSE;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.PROPOSAL_CREATED;
import static com.axlabs.neo.grantshares.util.TestHelper.PROPOSAL_ENDORSED;
import static com.axlabs.neo.grantshares.util.TestHelper.REMOVE_MEMBER;
import static com.axlabs.neo.grantshares.util.TestHelper.UNPAUSE;
import static com.axlabs.neo.grantshares.util.TestHelper.UPDATE_CONTRACT;
import static com.axlabs.neo.grantshares.util.TestHelper.VOTE;
import static com.axlabs.neo.grantshares.util.TestHelper.VOTED;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.util.TestHelper.createSimpleProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesGovTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static Account alice; // Set to be a DAO member.
    private static Account bob;
    private static Account charlie; // Set to be a DAO member.
    private static int defaultProposalId;

    private final static Path TESTCONTRACT_NEF_FILE = Paths.get("TestContract.nef");
    private final static Path TESTCONTRACT_MANIFEST_FILE =
            Paths.get("TestGrantSharesGov.manifest.json");

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE)));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);

        Hash256 creationTx = gov.invokeFunction(CREATE, hash160(alice.getScriptHash()),
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
    @Order(0)
    public void succeed_creating_and_retrieving_proposal() throws Throwable {
        // 1. Setup and create proposal
        Hash160 targetContract = NeoToken.SCRIPT_HASH;
        String targetMethod = "transfer";
        Hash160 targetParam1 = gov.getScriptHash();
        Hash160 targetParam2 = alice.getScriptHash();
        int targetParam3 = 1;
        ContractParameter intent = array(targetContract, targetMethod,
                array(targetParam1, targetParam2, targetParam3));
        String offchainUri = "offchainUri of the proposal";
        Hash256 proposalCreationTx = gov.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(offchainUri),
                        integer(-1)) // no linked proposal
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);

        int id = neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Test correct setup of the created proposal.
        NeoInvokeFunction r = gov.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        ProposalStruct p = new ProposalStruct(r.getInvocationResult().getStack().get(0).getList());
        assertThat(p.id, is(id));
        assertThat(p.proposer, is(alice.getScriptHash()));
        int n = neow3j.getTransactionHeight(proposalCreationTx).send().getHeight().intValue();
        assertThat(p.expiration.intValue(), is(n + PHASE_LENGTH));
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
    @Order(0)
    public void fail_creating_with_missing_linked_proposal() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(gov.getScriptHash(), alice.getScriptHash(), 1));
        String offchainUri = "fail_creating_with_missing_linked_proposal";
        byte[] linkedProposal = Hash256.ZERO.toArray();

        String exception = gov.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(offchainUri),
                        byteArray(linkedProposal))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Linked proposal doesn't exist"));
    }

    @Test
    @Order(0)
    public void fail_creating_with_bad_quorum() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(gov.getScriptHash(), alice.getScriptHash(), 1));
        String offchainUri = "fail_creating_with_bad_quorum";

        String exception = gov.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(offchainUri),
                        any(null), // linked proposal
                        integer(MIN_ACCEPTANCE_RATE),
                        integer(MIN_QUORUM - 1))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();

        assertThat(exception, containsString("Quorum not allowed"));
    }

    @Test
    @Order(0)
    public void fail_creating_with_bad_acceptance_rate() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(gov.getScriptHash(), alice.getScriptHash(), 1));
        String offchainUri = "fail_creating_with_bad_acceptance_rate";

        String exception = gov.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(offchainUri),
                        any(null), // linked proposal
                        integer(MIN_ACCEPTANCE_RATE - 1),
                        integer(MIN_QUORUM))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();

        assertThat(exception, containsString("Acceptance rate not allowed"));
    }

    @Test
    @Order(0)
    public void succeed_endorsing_with_member() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = createSimpleProposal(gov, alice, "succeed_endorsing_with_member");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Test that proposal endorser and phases have not yet been setup.
        ProposalStruct p = gov.getProposal(id);
        assertThat(p.endorser, is(nullValue()));
        assertThat(p.reviewEnd, is(BigInteger.ZERO));
        assertThat(p.votingEnd, is(BigInteger.ZERO));
        assertThat(p.timelockEnd, is(BigInteger.ZERO));
        assertThat(p.expiration, is(greaterThan(BigInteger.ZERO)));

        // 3. Endorse
        Hash256 endorseTx = gov.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 4. Test the right setup of the proposal phases
        p = gov.getProposal(id);
        assertThat(p.endorser, is(alice.getScriptHash()));
        int n = neow3j.getTransactionHeight(endorseTx).send().getHeight().intValue();
        assertThat(p.reviewEnd.intValue(), is(n + PHASE_LENGTH));
        assertThat(p.votingEnd.intValue(), is(n + PHASE_LENGTH + PHASE_LENGTH));
        assertThat(p.timelockEnd.intValue(), is(n + PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH));
        assertThat(p.expiration.intValue(), is(n + PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH + PHASE_LENGTH));

        // 5. Test the right setup of the votes map
        p = gov.getProposal(id);
        assertThat(p.approve, is(0));
        assertThat(p.reject, is(0));
        assertThat(p.abstain, is(0));

        // 6. Test emitted "endorsed" event
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(endorseTx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_ENDORSED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getInteger().intValue(), is(id));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
    }

    @Test
    @Order(0)
    public void fail_endorsing_with_non_member() throws Throwable {
        InvocationResult res = gov.callInvokeFunction(ENDORSE, asList(
                        integer(defaultProposalId), hash160(bob.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_endorsing_with_member_but_wrong_signer() throws Throwable {
        InvocationResult res = gov.callInvokeFunction(ENDORSE, asList(
                        integer(defaultProposalId), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_endorsing_already_endorsed_proposal() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = createSimpleProposal(gov, alice,
                "fail_endorsing_already_endrosed");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = gov.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Endorse again
        String exception = gov.callInvokeFunction(ENDORSE, asList(integer(id),
                        hash160(charlie.getScriptHash())), AccountSigner.calledByEntry(charlie))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Proposal already endorsed"));
    }

    @Test
    @Order(0)
    public void fail_endorsing_non_existent_proposal() throws Throwable {
        InvocationResult res = gov.callInvokeFunction(ENDORSE, asList(
                        byteArray(Hash256.ZERO.toArray()), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(alice)).getInvocationResult();
        assertThat(res.getException(), containsString("Proposal doesn't exist"));
    }

    @Test
    @Order(0)
    public void fail_endorsing_expired_proposal() throws Throwable {
        Hash256 creationTx = createSimpleProposal(gov, bob, "fail_endorsing_expired_proposal");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();
        ext.fastForward(PHASE_LENGTH);
        InvocationResult res = gov.endorseProposal(id, alice.getScriptHash())
                .signers(AccountSigner.calledByEntry(alice)).callInvokeScript().getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Proposal expired"));
    }

    @Test
    @Order(0)
    public void succeed_voting() throws Throwable {
        // 1. Create proposal
        Hash256 creationTx = createSimpleProposal(gov, bob, "succeed_voting");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = gov.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Wait till review phase ends.
        ext.fastForward(PHASE_LENGTH);

        // 4. Vote
        Hash256 voteTx = gov.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Test the right setting of the votes
        ProposalStruct proposal = gov.getProposal(id);
        assertThat(proposal.approve, is(0));
        assertThat(proposal.reject, is(1));
        assertThat(proposal.abstain, is(0));
        assertThat(proposal.voters.size(), is(1));
        assertThat(proposal.voters.get(charlie.getAddress()), is(-1));

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
    @Order(0)
    public void fail_voting_in_review_and_queued_phase() throws Throwable {
        Hash256 creationTx = createSimpleProposal(gov, bob, "fail_voting_in_review_phase");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send().getApplicationLog().getExecutions()
                .get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = gov.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Vote in review phase
        String exception = gov.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));

        // 5. Fast-forward till after the voting phase.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);

        // 4. Vote in queued or later phase
        exception = gov.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));
    }

    @Test
    @Order(0)
    public void fail_voting_multiple_times() throws Throwable {
        Hash256 creationTx = createSimpleProposal(gov, bob, "fail_voting_multiple_times");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = gov.invokeFunction(ENDORSE, integer(id),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Fast-forward to the voting phase.
        ext.fastForward(PHASE_LENGTH);

        // 4. Vote the first time
        Hash256 voteTx = gov.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Vote the second time
        String exception = gov.invokeFunction(VOTE, integer(id), integer(1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Already voted on this proposal"));

        // 6. Check votes
        ProposalStruct proposal = gov.getProposal(id);
        assertThat(proposal.approve, is(0));
        assertThat(proposal.reject, is(1));
        assertThat(proposal.abstain, is(0));
        assertThat(proposal.voters.size(), is(1));
        assertThat(proposal.voters.get(charlie.getAddress()), is(-1));
    }

    @Test
    @Order(0)
    public void fail_voting_with_non_member() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = gov.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(-1), hash160(bob.getScriptHash()))
                .signers(AccountSigner.calledByEntry(bob)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Not authorised"));
    }

    @Test
    @Order(0)
    public void fail_voting_on_non_existent_proposal() throws IOException {
        String exception = gov.invokeFunction(VOTE, byteArray("0102030405"),
                        integer(-1), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Proposal doesn't exist"));
    }

    @Test
    @Order(0)
    public void fail_voting_on_not_enorsed_proposal() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = gov.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(-1), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Proposal not active"));
    }

    @Test
    @Order(0)
    public void fail_voting_with_invalid_vote() throws IOException {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        String exception = gov.invokeFunction(VOTE, integer(defaultProposalId),
                        integer(2), hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie)).callInvokeScript().getInvocationResult()
                .getException();
        assertThat(exception, containsString("Invalid vote"));
    }

    @Test
    @Order(0)
    public void create_proposal_with_large_intents_and_offchainUri() throws Throwable {
        String offchainUri =
                "aabcababcababcababcabbcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcaabcabcabcabcabcabca";
        Hash256 tx = gov.invokeFunction(CREATE, hash160(bob),
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
                        string(offchainUri),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        int id = neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0)
                .getStack().get(0).getInteger().intValue();

        NeoInvokeFunction r = gov.callInvokeFunction(GET_PROPOSAL, asList(integer(id)));
        ProposalStruct p = new ProposalStruct(r.getInvocationResult().getStack().get(0).getList());
        assertThat(p.id, is(id));
        assertThat(p.proposer, is(bob.getScriptHash()));
        assertThat(p.linkedProposal, is(-1));
        assertThat(p.acceptanceRate, is(MIN_ACCEPTANCE_RATE));
        assertThat(p.quorum, is(MIN_QUORUM));
        assertThat(p.intents.size(), is(9));
        assertThat(p.offchainUri, is(offchainUri));
    }

    @Test
    @Order(0)
    public void succeed_creating_exact_same_proposal_that_already_exists() throws IOException {
        // Recreate default proposal
        InvocationResult result = gov.invokeFunction(CREATE, hash160(alice.getScriptHash()),
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                                array(new Hash160(defaultAccountScriptHash())))),
                        string("default_proposal"),
                        integer(-1)).signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult();
        assertThat(result.getStack().get(0).getInteger().intValue(), is(not(defaultProposalId)));
        assertThat(result.getStack().get(0).getInteger().intValue(), is(greaterThan(0)));
    }

    @Test
    @Order(0)
    public void get_proposals() throws Throwable {
        ContractParameter intents = array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                array(new Hash160(defaultAccountScriptHash()))));
        TestHelper.createAndEndorseProposal(gov, neow3j, bob, alice, intents, "some_proposal");

        List<StackItem> page = gov.callInvokeFunction(GET_PROPOSALS,
                        asList(integer(0), integer(1))) // get page 0 with page size 1
                .getInvocationResult().getStack().get(0).getList();
        assertThat(page.get(0).getInteger().intValue(), is(0));
        assertThat(page.get(1).getInteger().intValue(), is(greaterThanOrEqualTo(2))); // at least
        // two pages
        assertThat(page.get(2).getList().size(), is(1)); // the list of proposals should be 1
        ProposalStruct prop = new ProposalStruct(page.get(2).getList().get(0).getList());
        assertThat(prop.id, is(defaultProposalId));

        prop = new ProposalStruct(gov.callInvokeFunction(GET_PROPOSALS,
                        asList(integer(1), integer(1))) // get page 1 with page size 1
                .getInvocationResult().getStack().get(0).getList().get(2).getList().get(0).getList());
        assertThat(prop.id, is(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(0)
    public void get_number_of_proposals() throws IOException {
        int result = gov.callInvokeFunction(GET_PROPOSAL_COUNT)
                .getInvocationResult().getStack().get(0).getInteger().intValue();
        // At least one because of the default proposal from the setup method.
        assertThat(result, is(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(0)
    public void fail_pausing_contract_without_members_account() throws Throwable {
        String exception = gov.invokeFunction(PAUSE)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorized"));
    }

    @Test
    @Order(0)
    public void fail_unpausing_contract_without_members_account() throws Throwable {
        String exception = gov.invokeFunction(UNPAUSE)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Not authorized"));
    }

    // Is executed as the first test of series of test that require the contract to be paused.
    @Order(10)
    @Test
    public void succeed_pausing_contract() throws Throwable {
        assertFalse(gov.callInvokeFunction(IS_PAUSED).getInvocationResult()
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

        assertTrue(gov.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());
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
                gov.invokeFunction(UPDATE_CONTRACT, byteArray(nef.toArray()), byteArray(manifestBytes), data)
                        .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Method only callable by the contract itself"));
    }

    @Test
    @Order(0)
    public void fail_create_proposal_calling_contract_management() throws Throwable {
        IntentParam intent1 = IntentParam.addMemberProposal(gov.getScriptHash(), alice.getECKeyPair().getPublicKey());
        IntentParam intent2 = new IntentParam(ContractManagement.SCRIPT_HASH, "destroy");
        String exception = gov.createProposal(alice.getScriptHash(), "fail_create_proposal_calling_contract_management",
                        -1, intent1, intent2)
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Calls to ContractManagement not allowed"));
    }

    @Order(11)
    @Test
    public void fail_change_param_on_paused_contract() throws Throwable {
        String exception = gov.callInvokeFunction(CHANGE_PARAM,
                        asList(string(MIN_ACCEPTANCE_RATE_KEY), integer(50)))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(12)
    @Test
    public void fail_add_member_on_paused_contract() throws Throwable {
        String exception = gov.callInvokeFunction(ADD_MEMBER,
                        asList(publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true))))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(13)
    @Test
    public void fail_remove_member_on_paused_contract() throws Throwable {
        String exception = gov.callInvokeFunction(REMOVE_MEMBER,
                        asList(publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true))))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(14)
    @Test
    public void fail_execute_proposal_on_paused_contract() throws Throwable {
        String exception = gov.execute(defaultProposalId).callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(15)
    @Test
    public void fail_vote_on_proposal_on_paused_contract() throws Throwable {
        String exception = gov.vote(defaultProposalId, 1, alice.getScriptHash()).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(16)
    @Test
    public void fail_endorse_proposal_on_paused_contract() throws Throwable {
        String exception = gov.endorseProposal(defaultProposalId, alice.getScriptHash()).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    @Order(17)
    @Test
    public void fail_update_contract_on_paused_contract() throws Throwable {
        String exception = gov.updateContract(new byte[]{0x01, 0x02, 0x03}, "the manifest", null).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Contract is paused"));
    }

    // Must be executed after all tests orderd after the test that pauses the contract.
    @Order(19)
    @Test
    public void succeed_unpausing_contract() throws Throwable {
        assertTrue(gov.callInvokeFunction(IS_PAUSED).getInvocationResult()
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

        assertFalse(gov.callInvokeFunction(IS_PAUSED).getInvocationResult()
                .getStack().get(0).getBoolean());
    }

    @Test
    @Order(20)
    public void execute_proposal_with_update_contract() throws Throwable {
        File nefFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_NEF_FILE.toString()).toURI());
        NefFile nef = NefFile.readFromFile(nefFile);

        File manifestFile = new File(this.getClass().getClassLoader()
                .getResource(TESTCONTRACT_MANIFEST_FILE.toString()).toURI());
        ContractManifest manifest = getObjectMapper()
                .readValue(manifestFile, ContractManifest.class);
        byte[] manifestBytes = getObjectMapper().writeValueAsBytes(manifest);

        ContractParameter data = string("some data");

        ContractParameter intents = array(
                array(gov.getScriptHash(), UPDATE_CONTRACT,
                        array(nef.toArray(), manifestBytes, data)));
        String offchainUri = "execute_proposal_with_update_contract";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
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
        assertThat(n.getEventName(), is("Update"));
    }

}