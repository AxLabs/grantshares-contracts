package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3jExpress;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployContext;
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

import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.fail;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, neoxpConfig = "default.neo-express",
        batchFile = "setup.batch")
public class GrantSharesGovTest {

    //region CONSTANTS
    // Account names available in the neo-express config file.
    private static final String ALICE = "Alice";
    private static final String BOB = "Bob";
    private static final String CHARLIE = "Charlie";

    // contract methods
    private static final String CREATE = "createProposal";
    private static final String GET = "getProposal";
    private static final String ENDORSE = "endorseProposal";
    private static final String GET_PHASES = "getProposalPhases";
    private static final String GET_VOTES = "getProposalVotes";
    private static final String VOTE = "vote";
    private static final String EXECUTE = "execute";
    private static final String HASH_PROPOSAL = "hashProposal";
    private static final String GET_PARAMETER = "getParameter";

    // events
    private static final String PROPOSAL_CREATED = "ProposalCreated";
    private static final String PROPOSAL_INTENT = "ProposalIntent";
    private static final String PROPOSAL_ENDORSED = "ProposalEndorsed";
    private static final String VOTED = "Voted";

    // governance parameters values
    private static final int REVIEW_LENGTH = 5;
    private static final int VOTING_LENGTH = 5;
    private static final int QUEUED_LENGTH = 5;
    private static final int MIN_ACCEPTANCE_RATE = 50;
    private static final int MIN_QUORUM = 25;

    // parameter names
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String QUEUED_LENGTH_KEY = "queued_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MAX_FUNDING_AMOUNT_KEY = "max_funding";

    //endregion CONSTANTS

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3jExpress neow3j;
    private static SmartContract contract;
    private static Account alice; // Set to be a DAO member.
    private static Account bob;
    private static Account charlie; // Set to be a DAO member.
    private static String defaultProposalHash;

    @DeployConfig(GrantSharesGov.class)
    public static ContractParameter config(DeployContext ctx) throws IOException {
        return array(
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
        );
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
                        string("default_proposal"))
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
                        string(proposalDescription))
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
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        assertThat(state.get(2).getHexString(),
                is(Numeric.toHexStringNoPrefix(md.digest(proposalDescription.getBytes()))));
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
        Map<StackItem, StackItem> map = r.getInvocationResult().getStack().get(0).getMap();
        assertThat(map.keySet().stream().map(StackItem::getAddress).collect(Collectors.toList()),
                contains(alice.getAddress()));
        assertThat(map.values().stream().map(StackItem::getInteger).collect(Collectors.toList()),
                contains(BigInteger.ONE));

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
        assertThat(contract.callInvokeFunction(GET_PHASES, asList(byteArray(defaultProposalHash)))
                .getInvocationResult().getStack().get(0).getValue(), is(nullValue()));
    }

    @Test
    public void fail_endorsing_with_member_but_wrong_signer() throws Throwable {
        InvocationResult res = contract.callInvokeFunction(ENDORSE, asList(
                        byteArray(defaultProposalHash), hash160(alice.getScriptHash())),
                AccountSigner.calledByEntry(bob)).getInvocationResult();
        assertThat(res.getState(), is(NeoVMStateType.FAULT));
        assertThat(res.getException(), containsString("Not authorised"));
        assertThat(contract.callInvokeFunction(GET_PHASES, asList(byteArray(defaultProposalHash)))
                .getInvocationResult().getStack().get(0).getValue(), is(nullValue()));
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
        Map<StackItem, StackItem> votersMap = r.getInvocationResult().getStack().get(0).getMap();
        List<String> voters = votersMap.keySet().stream().map(StackItem::getAddress)
                .collect(Collectors.toList());
        assertThat(voters, containsInAnyOrder(alice.getAddress(), charlie.getAddress()));
        List<Integer> votes = votersMap.values().stream().map(s -> s.getInteger().intValue())
                .collect(Collectors.toList());
        assertThat(votes, containsInAnyOrder(1, -1));

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
    public void fail_voting_in_review_state() {
        // TODO: Try voting on a proposal that is in review state
        fail();
    }

    @Test
    public void fail_voting_in_queued_state() {
        // TODO: Try voting on a proposal that is in queued state
    }

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

        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(tx).send().getApplicationLog()
                        .getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_CREATED));
        List<StackItem> state = ntf.getState().getList();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        assertThat(state.get(2).getHexString(),
                is(Numeric.toHexStringNoPrefix(md.digest(desc.getBytes()))));
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
                        string("create_proposal_with_large_intents"))
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
                        string(description))
                .signers(AccountSigner.calledByEntry(signer))
                .sign()
                .send().getSendRawTransaction().getHash();
    }

}