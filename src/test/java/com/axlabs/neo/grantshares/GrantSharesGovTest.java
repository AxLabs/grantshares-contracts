package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import com.axlabs.neo.grantshares.util.ProposalPaginatedStruct;
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
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.Transaction;
import io.neow3j.transaction.Witness;
import io.neow3j.types.CallFlags;
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

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.axlabs.neo.grantshares.util.TestHelper.ADD_MEMBER;
import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.CREATE;
import static com.axlabs.neo.grantshares.util.TestHelper.ENDORSE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_PROPOSAL;
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
import static com.axlabs.neo.grantshares.util.TestHelper.assertAborted;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.createMultiSigAccount;
import static com.axlabs.neo.grantshares.util.TestHelper.createSimpleProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.array;
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
        neow3j.allowTransmissionOnFault();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);

        Hash256 creationTx = gov.invokeFunction(CREATE, hash160(alice.getScriptHash()),
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                integer(CallFlags.ALL.getValue()))),
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
                array(targetParam1, targetParam2, targetParam3), integer(CallFlags.ALL.getValue()));
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
        BigInteger n = neow3j.getTransactionHeight(proposalCreationTx).send().getHeight();
        long time = neow3j.getBlock(n, false).send().getBlock().getTime();
        assertThat(p.expiration.longValue(), is(time + PHASE_LENGTH * 1000));
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

        Hash256 tx = gov.createProposal(alice.getScriptHash(), offchainUri, 1000, intent)
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Linked proposal doesn't exist", neow3j);
    }

    @Test
    @Order(0)
    public void fail_creating_with_bad_quorum() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(gov.getScriptHash(), alice.getScriptHash(), 1));
        String offchainUri = "fail_creating_with_bad_quorum";

        Hash256 tx = gov.createProposal(alice.getScriptHash(), offchainUri, -1, MIN_ACCEPTANCE_RATE, MIN_QUORUM - 1,
                intent).signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid quorum", neow3j);

        tx = gov.createProposal(alice.getScriptHash(), offchainUri, -1, MIN_ACCEPTANCE_RATE, 101, intent)
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid quorum", neow3j);
    }

    @Test
    @Order(0)
    public void fail_creating_with_bad_acceptance_rate() throws Throwable {
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(gov.getScriptHash(), alice.getScriptHash(), 1));
        String offchainUri = "fail_creating_with_bad_acceptance_rate";

        Hash256 tx = gov.createProposal(alice.getScriptHash(), offchainUri, -1, MIN_ACCEPTANCE_RATE - 1, MIN_QUORUM,
                intent).signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid acceptance rate", neow3j);

        tx = gov.createProposal(alice.getScriptHash(), offchainUri, -1, 101, MIN_QUORUM, intent)
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid acceptance rate", neow3j);
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
        BigInteger n = neow3j.getTransactionHeight(endorseTx).send().getHeight();
        long time = neow3j.getBlock(n, false).send().getBlock().getTime();
        long phase = PHASE_LENGTH * 1000;
        assertThat(p.reviewEnd.longValue(), is(time + phase));
        assertThat(p.votingEnd.longValue(), is(time + 2 * phase));
        assertThat(p.timelockEnd.longValue(), is(time + 3 * phase));
        assertThat(p.expiration.longValue(), is(time + 4 * phase));

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
        Hash256 tx = gov.endorseProposal(defaultProposalId, bob.getScriptHash())
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Not authorised", neow3j);
    }

    @Test
    @Order(0)
    public void fail_endorsing_with_member_but_wrong_signer() throws Throwable {
        Hash256 tx = gov.endorseProposal(defaultProposalId, alice.getScriptHash())
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Not authorised", neow3j);
    }

    @Test
    @Order(0)
    public void fail_endorsing_already_endorsed_proposal() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = createSimpleProposal(gov, alice, "fail_endorsing_already_endorsed_proposal");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send().getApplicationLog().getExecutions().get(0).getStack()
                .get(0).getInteger().intValue();

        // 2. Endorse
        Hash256 endorseTx = gov.endorseProposal(id, alice.getScriptHash()).signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Endorse again
        Hash256 tx = gov.endorseProposal(id, alice.getScriptHash()).signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal already endorsed", neow3j);
    }

    @Test
    @Order(0)
    public void fail_endorsing_non_existent_proposal() throws Throwable {
        Hash256 tx = gov.endorseProposal(1000, alice.getScriptHash()).signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal doesn't exist", neow3j);
    }

    @Test
    @Order(0)
    public void fail_endorsing_expired_proposal() throws Throwable {
        Hash256 creationTx = createSimpleProposal(gov, bob, "fail_endorsing_expired_proposal");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        int id = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getInteger().intValue();
        ext.fastForwardOneBlock(PHASE_LENGTH);

        Hash256 tx = gov.endorseProposal(id, alice.getScriptHash()).signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal expired", neow3j);
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
        ext.fastForwardOneBlock(PHASE_LENGTH);

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
        Hash256 creationTx = createSimpleProposal(gov, bob, "fail_voting_in_review_and_queued_phase");
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
        Hash256 tx = gov.vote(id, -1, charlie.getScriptHash()).signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal not active", neow3j);

        // 5. Fast-forward till after the voting phase.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        // 4. Vote in queued or later phase
        tx = gov.vote(id, -1, charlie.getScriptHash()).signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal not active", neow3j);
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
        ext.fastForwardOneBlock(PHASE_LENGTH);

        // 4. Vote the first time
        Hash256 voteTx = gov.invokeFunction(VOTE, integer(id), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Vote the second time
        Hash256 tx = gov.vote(id, 1, charlie.getScriptHash()).signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Already voted on this proposal", neow3j);

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
    public void fail_voting_with_non_member() throws Throwable {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        Hash256 tx = gov.vote(defaultProposalId, -1, bob.getScriptHash()).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Not authorised", neow3j);
    }

    @Test
    @Order(0)
    public void fail_voting_on_non_existent_proposal() throws Throwable {
        Hash256 tx = gov.vote(1000, -1, charlie.getScriptHash()).signers(AccountSigner.calledByEntry(charlie)).sign()
                .send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal doesn't exist", neow3j);
    }

    @Test
    @Order(0)
    public void fail_voting_on_not_endorsed_proposal() throws Throwable {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        Hash256 tx = gov.vote(defaultProposalId, -1, charlie.getScriptHash())
                .signers(AccountSigner.calledByEntry(charlie)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Proposal not active", neow3j);
    }

    @Test
    @Order(0)
    public void fail_voting_with_invalid_vote() throws Throwable {
        // Vote on the default proposal. Doesn't matter in what phase it is.
        Hash256 tx = gov.vote(defaultProposalId, 2, charlie.getScriptHash())
                .signers(AccountSigner.calledByEntry(charlie)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid vote", neow3j);
    }

    @Test
    @Order(0)
    public void create_proposal_with_large_intents_and_offchainUri() throws Throwable {
        String offchainUri =
                "aabcababcababcababcabbcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcaabcabcabcabcabcabca";
        Hash256 tx = gov.invokeFunction(CREATE, hash160(bob),
                        array(
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()),
                                array(NeoToken.SCRIPT_HASH, "jjsldfjklkasjdfkljalkjasdf;lkjasddfd" +
                                                "lfjkasdflkjasdfssldfjklkasjdfkljasasdfasfasdfasdf",
                                        array(
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash()),
                                                new Hash160(defaultAccountScriptHash())
                                        ), CallFlags.ALL.getValue()),
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
                                        ), CallFlags.ALL.getValue()),
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
                                        ), CallFlags.ALL.getValue())
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
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf", array(new Hash160(defaultAccountScriptHash())),
                                CallFlags.ALL.getValue())),
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
                array(new Hash160(defaultAccountScriptHash())), CallFlags.ALL.getValue()));
        TestHelper.createAndEndorseProposal(gov, neow3j, bob, alice, intents, "some_proposal");

        ProposalPaginatedStruct page = gov.getProposals(0, 1);
        assertThat(page.page, is(0));
        assertThat(page.pages, is(greaterThanOrEqualTo(2)));
        assertThat(page.items.size(), is(1));
        ProposalStruct proposal = page.items.get(0);
        assertThat(proposal.id, is(defaultProposalId));

        proposal = gov.getProposals(1, 1).items.get(0);
        assertThat(proposal.id, is(greaterThanOrEqualTo(1)));

        String exception = gov.callInvokeFunction("getProposals", asList(integer(-1), integer(1)))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Page number was negative"));

        exception = gov.callInvokeFunction("getProposals", asList(integer(0), integer(0)))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Page number was negative or zero"));
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
        Hash256 tx = gov.invokeFunction(PAUSE).signers(AccountSigner.calledByEntry(alice)).sign().send()
                .getSendRawTransaction().getHash();
        assertAborted(tx, "Not authorized", neow3j);
    }

    @Test
    @Order(0)
    public void fail_unpausing_contract_without_members_account() throws Throwable {
        Hash256 tx = gov.invokeFunction(UNPAUSE).signers(AccountSigner.calledByEntry(alice)).sign().send()
                .getSendRawTransaction().getHash();
        assertAborted(tx, "Not authorized", neow3j);
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
        String manifestString = getObjectMapper().writeValueAsString(manifest);
        Hash256 tx = gov.updateContract(nef.toArray(), manifestString, null).signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Method only callable by the contract itself", neow3j);
    }

    @Test
    @Order(0)
    public void fail_create_proposal_calling_contract_management() throws Throwable {
        IntentParam intent1 = IntentParam.addMemberProposal(gov.getScriptHash(), alice.getECKeyPair().getPublicKey());
        IntentParam intent2 = new IntentParam(ContractManagement.SCRIPT_HASH, "destroy");

        Hash256 tx = gov.createProposal(alice.getScriptHash(), "fail_create_proposal_calling_contract_management", -1,
                        intent1, intent2).signers(AccountSigner.calledByEntry(alice)).sign().send()
                .getSendRawTransaction().getHash();
        assertAborted(tx, "Invalid intents", neow3j);
    }

    @Order(11)
    @Test
    public void fail_change_param_on_paused_contract() throws Throwable {
        Hash256 tx = gov.invokeFunction(CHANGE_PARAM, string(MIN_ACCEPTANCE_RATE_KEY), integer(50))
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(12)
    @Test
    public void fail_add_member_on_paused_contract() throws Throwable {
        Hash256 tx = gov.invokeFunction(ADD_MEMBER, publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true)))
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(13)
    @Test
    public void fail_remove_member_on_paused_contract() throws Throwable {
        Hash256 tx = gov.invokeFunction(REMOVE_MEMBER, publicKey(bob.getECKeyPair().getPublicKey().getEncoded(true)))
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(14)
    @Test
    public void fail_execute_proposal_on_paused_contract() throws Throwable {
        Hash256 tx = gov.execute(defaultProposalId).signers(AccountSigner.calledByEntry(bob)).sign().send()
                .getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(15)
    @Test
    public void fail_vote_on_proposal_on_paused_contract() throws Throwable {
        Hash256 tx = gov.vote(defaultProposalId, 1, alice.getScriptHash()).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(16)
    @Test
    public void fail_endorse_proposal_on_paused_contract() throws Throwable {
        Hash256 tx = gov.endorseProposal(defaultProposalId, alice.getScriptHash())
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
    }

    @Order(17)
    @Test
    public void fail_update_contract_on_paused_contract() throws Throwable {
        Hash256 tx = gov.updateContract(new byte[]{0x01, 0x02, 0x03}, "the manifest", null)
                .signers(AccountSigner.calledByEntry(bob)).sign().send().getSendRawTransaction().getHash();
        assertAborted(tx, "Contract is paused", neow3j);
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
                array(
                        gov.getScriptHash(),
                        UPDATE_CONTRACT,
                        array(nef.toArray(), manifestBytes, data),
                        CallFlags.ALL.getValue()
                ));
        String offchainUri = "execute_proposal_with_update_contract";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("UpdatingContract"));
        assertThat(n.getContract(), is(gov.getScriptHash()));
    }

}