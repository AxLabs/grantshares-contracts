package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
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
import io.neow3j.utils.Files;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.TestConstants.ALICE;
import static com.axlabs.neo.grantshares.TestConstants.BOB;
import static com.axlabs.neo.grantshares.TestConstants.CHARLIE;
import static com.axlabs.neo.grantshares.TestConstants.CREATE;
import static com.axlabs.neo.grantshares.TestConstants.ENDORSE;
import static com.axlabs.neo.grantshares.TestConstants.GET;
import static com.axlabs.neo.grantshares.TestConstants.GET_PARAMETER;
import static com.axlabs.neo.grantshares.TestConstants.GET_PHASES;
import static com.axlabs.neo.grantshares.TestConstants.GET_VOTES;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM_KEY;
import static com.axlabs.neo.grantshares.TestConstants.PROPOSAL_CREATED;
import static com.axlabs.neo.grantshares.TestConstants.PROPOSAL_ENDORSED;
import static com.axlabs.neo.grantshares.TestConstants.PROPOSAL_INTENT;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.VOTE;
import static com.axlabs.neo.grantshares.TestConstants.VOTED;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH_KEY;
import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

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
    private static String defaultProposalHash;
    private static MessageDigest hasher;

    @DeployConfig(GrantSharesGov.class)
    public static void deployConfig(DeployConfiguration config) throws Exception {
        config.setDeployParam(array(
                array(
                        ext.getAccount(ALICE).getScriptHash(),
                        ext.getAccount(CHARLIE).getScriptHash()),
                array(
                        REVIEW_LENGTH_KEY, REVIEW_LENGTH,
                        VOTING_LENGTH_KEY, VOTING_LENGTH,
                        QUEUED_LENGTH_KEY, QUEUED_LENGTH,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM
                )
        ));
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(GrantSharesGov.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        hasher = MessageDigest.getInstance("SHA-256");

        Hash256 creationTx = contract.invokeFunction(CREATE, hash160(alice.getScriptHash()),
                        array(array(NeoToken.SCRIPT_HASH, "balanceOf",
                                array(new Hash160(defaultAccountScriptHash())))),
                        byteArray(hasher.digest("default_proposal".getBytes(UTF_8))),
                        any(null))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        defaultProposalHash = neow3j.getApplicationLog(creationTx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getHexString();
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
                        byteArray(hasher.digest(proposalDescription.getBytes(UTF_8))),
                        any(null)) // no linked proposal
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);

        String proposalHash = neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        // 2. Test correct setup of the created proposal.
        NeoInvokeFunction r = contract.callInvokeFunction(GET, asList(byteArray(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        assertThat(list.get(0).getHexString(), is(proposalHash));
        assertThat(list.get(1).getAddress(), is(alice.getAddress()));
        assertThat(list.get(2).getValue(), is(nullValue()));
        assertThat(list.get(3).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(list.get(4).getInteger().intValue(), is(MIN_QUORUM));

        // 3. Test CreateProposal event values
        List<NeoApplicationLog.Execution.Notification> ntfs =
                neow3j.getApplicationLog(proposalCreationTx).send().getApplicationLog()
                        .getExecutions().get(0).getNotifications();

        NeoApplicationLog.Execution.Notification ntf = ntfs.get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_CREATED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getHexString(), is(proposalHash));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
        assertThat(state.get(2).getHexString(),
                is(Numeric.toHexStringNoPrefix(hasher.digest(proposalDescription.getBytes(UTF_8)))));
        assertThat(state.get(3).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(state.get(4).getInteger().intValue(), is(MIN_QUORUM));

        // 4. Test ProposalIntent event
        ntf = ntfs.get(1);
        assertThat(ntf.getEventName(), is(PROPOSAL_INTENT));
        assertThat(ntf.getState().getList().get(0).getHexString(), is(proposalHash));
        List<StackItem> intentItem = ntf.getState().getList().get(1).getList();
        assertThat(intentItem.get(0).getAddress(), is(targetContract.toAddress()));
        assertThat(intentItem.get(1).getString(), is(targetMethod));
        List<StackItem> params = intentItem.get(2).getList();
        assertThat(params.get(0).getAddress(), is(targetParam1.toAddress()));
        assertThat(params.get(1).getAddress(), is(targetParam2.toAddress()));
        assertThat(params.get(2).getInteger().intValue(), is(targetParam3));
    }

    @Test
    public void fail_creating_with_missing_linked_proposal() throws Throwable {
        Hash160 targetContract = NeoToken.SCRIPT_HASH;
        String targetMethod = "transfer";
        Hash160 targetParam1 = contract.getScriptHash();
        Hash160 targetParam2 = alice.getScriptHash();
        int targetParam3 = 1;
        ContractParameter intent = array(targetContract, targetMethod,
                array(targetParam1, targetParam2, targetParam3));
        String proposalDescription = "description of the proposal";

        String exception = contract.invokeFunction(CREATE,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        byteArray(hasher.digest(proposalDescription.getBytes(UTF_8))),
                        byteArray(Hash256.ZERO.toArray()))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Linked proposal doesn't exist"));
    }

    // TODO:
    //  - fail_creating_with_bad_acceptance_rate
    //  - fail_creating_with_bad_quorum
    //  - Create proposal with weird intents

    @Test
    public void succeed_endorsing_with_member() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = sendCreateTestProposalTransaction(alice.getScriptHash(),
                alice, "succeed_endorsing_with_member");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        String proposalHash = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        // 2. Test that proposal phases have not yet been setup.
        assertThat(contract.callInvokeFunction(GET_PHASES, asList(byteArray(proposalHash)))
                .getInvocationResult().getStack().get(0).getValue(), is(nullValue()));

        // 3. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, byteArray(proposalHash),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 4. Test the right setup of the proposal phases
        NeoInvokeFunction r = contract.callInvokeFunction(GET_PHASES,
                asList(byteArray(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        int n = neow3j.getTransactionHeight(endorseTx).send().getHeight().intValue();
        assertThat(list.get(0).getInteger().intValue(), is(n + REVIEW_LENGTH));
        assertThat(list.get(1).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH));
        assertThat(list.get(2).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH
                + QUEUED_LENGTH));

        // 5. Test the right setup of the votes map
        r = contract.callInvokeFunction(GET_VOTES, asList(byteArray(proposalHash)));
        List<StackItem> votes = r.getInvocationResult().getStack().get(0).getList();
        assertThat(votes.get(0).getInteger(), is(BigInteger.ZERO));
        assertThat(votes.get(1).getInteger(), is(BigInteger.ZERO));
        assertThat(votes.get(2).getInteger(), is(BigInteger.ZERO));

        // 6. Test emitted "endrosed" event
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(endorseTx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_ENDORSED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getHexString(), is(proposalHash));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
        assertThat(state.get(2).getInteger().intValue(), is(n + REVIEW_LENGTH));
        assertThat(state.get(3).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH));
        assertThat(state.get(4).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH
                + QUEUED_LENGTH));
    }

    @Test
    public void fail_endorsing_with_non_member() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        byteArray(defaultProposalHash), hash160(bob.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    public void fail_endorsing_with_member_but_wrong_signer() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        byteArray(defaultProposalHash), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
    }

    @Test
    public void fail_endorsing_already_endorsed_proposal() throws Throwable {
        // 1. Create a proposal
        Hash256 creationTx = sendCreateTestProposalTransaction(alice.getScriptHash(),
                alice, "fail_endorsing_already_endrosed");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        String proposalHash = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, byteArray(proposalHash),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Endorse again
        String exception = contract.callInvokeFunction(ENDORSE, asList(byteArray(proposalHash),
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
        Hash256 creationTx = sendCreateTestProposalTransaction(bob.getScriptHash(),
                bob, "succeed_voting");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        String proposalHash = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, byteArray(proposalHash),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Wait till review phase ends.
        ext.fastForward(REVIEW_LENGTH);

        // 4. Vote
        Hash256 voteTx = contract.invokeFunction(VOTE, byteArray(proposalHash), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(voteTx, neow3j);

        // 5. Test the right setting of the votes
        NeoInvokeFunction r =
                contract.callInvokeFunction(GET_VOTES, asList(byteArray(proposalHash)));
        List<StackItem> votes = r.getInvocationResult().getStack().get(0).getList();
        assertThat(votes.get(0).getInteger(), is(BigInteger.ZERO));
        assertThat(votes.get(1).getInteger(), is(BigInteger.ONE));
        assertThat(votes.get(2).getInteger(), is(BigInteger.ZERO));

        // 6. Test the emitted vote event
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(voteTx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(VOTED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(0).getHexString(), is(proposalHash));
        assertThat(state.get(1).getAddress(), is(charlie.getAddress()));
        assertThat(state.get(2).getInteger().intValue(), is(-1));
    }

    @Test
    public void fail_voting_in_review_and_queued_phase() throws Throwable {
        Hash256 creationTx = sendCreateTestProposalTransaction(bob.getScriptHash(),
                bob, "fail_voting_in_review_phase");
        Await.waitUntilTransactionIsExecuted(creationTx, neow3j);
        String proposalHash = neow3j.getApplicationLog(creationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        // 2. Endorse
        Hash256 endorseTx = contract.invokeFunction(ENDORSE, byteArray(proposalHash),
                        hash160(alice.getScriptHash()))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // 3. Vote in review phase
        String exception = contract.invokeFunction(VOTE, byteArray(proposalHash), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));

        // 5. Fast-forward till after the voting phase.
        ext.fastForward(REVIEW_LENGTH + VOTING_LENGTH);

        // 4. Vote in queued or later phase
        exception = contract.invokeFunction(VOTE, byteArray(proposalHash), integer(-1),
                        hash160(charlie.getScriptHash()))
                .signers(AccountSigner.calledByEntry(charlie))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal not active"));
    }

    // TODO:
    //  - fail_voting_with_non_member
    //  - fail_voting_on_non_existent_proposal
    //  - fail_voting_on_not_endorsed_proposal
    //  - fail_voting_with_invalid_vote

    @Test
    public void create_proposal_with_large_description() throws Throwable {
        String desc = Files.readString(new File(this.getClass().getClassLoader().getResource(
                "proposal_description.txt").toURI()));
        Hash256 tx = sendCreateTestProposalTransaction(bob.getScriptHash(), bob, desc);
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        String proposalHash = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getHexString();

        NeoInvokeFunction r = contract.callInvokeFunction(GET, asList(byteArray(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        assertThat(list.get(0).getHexString(), is(proposalHash));

        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_CREATED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(state.get(2).getHexString(),
                is(Numeric.toHexStringNoPrefix(hasher.digest(desc.getBytes(UTF_8)))));
    }

    @Test
    public void create_proposal_with_large_intents() throws Throwable {
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
                        byteArray(hasher.digest("create_proposal_with_large_intents".getBytes(UTF_8))),
                        any(null))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        String proposalHash = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString();

        NeoInvokeFunction r = contract.callInvokeFunction(GET, asList(byteArray(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        assertThat(list.get(0).getHexString(), is(proposalHash));
        assertThat(list.get(1).getAddress(), is(bob.getAddress()));
        assertThat(list.get(2).getValue(), is(nullValue()));
        assertThat(list.get(3).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(list.get(4).getInteger().intValue(), is(MIN_QUORUM));

        // TODO: Test the intent events.
    }

    @Test
    public void get_parameters() throws IOException {
        assertThat(contract.callInvokeFunction(GET_PARAMETER, asList(string(REVIEW_LENGTH_KEY)))
                        .getInvocationResult().getStack().get(0).getInteger().intValue(),
                is(REVIEW_LENGTH));
    }

    private Hash256 sendCreateTestProposalTransaction(Hash160 proposer,
            Account signer, String description) throws Throwable {

        return contract.invokeFunction(CREATE, hash160(proposer),
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))
                                )
                        ),
                        byteArray(hasher.digest(description.getBytes(UTF_8))),
                        any(null))
                .signers(AccountSigner.calledByEntry(signer))
                .sign()
                .send().getSendRawTransaction().getHash();
    }

}