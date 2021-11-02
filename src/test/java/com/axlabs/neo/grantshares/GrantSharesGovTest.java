package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3jExpress;
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
        alice = ext.getAccount("Alice");
    }

    @Test
    @DisplayName("Succeed creating a proposal and retrieving it.")
    public void succeed_creating_and_retrieving_proposal() throws Throwable {
        ///////////////////////////////
        // Setup and create proposal //
        ///////////////////////////////
        Hash160 targetContract = NeoToken.SCRIPT_HASH;
        String targetMethod = "transfer";
        Hash160 targetParam1 = contract.getScriptHash();
        Hash160 targetParam2 = alice.getScriptHash();
        int targetParam3 = 1;
        ContractParameter intent = array(targetContract, targetMethod,
                array(targetParam1, targetParam2, targetParam3));
        // TODO: Add at least one other intent.
        String proposalDescription = "description of the proposal";
        Hash256 proposalCreationTx = contract.invokeFunction(CREATE_PROPOSAL,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string(proposalDescription))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);

        String proposalHash = reverseHexString(neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString());

        ///////////////////////////////////////////////
        // Retrieve created proposal and test values //
        ///////////////////////////////////////////////
        NeoInvokeFunction r =
                contract.callInvokeFunction(GET_PROPOSAL, asList(hash256(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        assertThat(reverseHexString(list.get(0).getHexString()), is(proposalHash));
        assertThat(list.get(1).getAddress(), is(alice.getAddress()));
        assertThat(list.get(2).getValue(), is(nullValue()));
        assertThat(list.get(3).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(list.get(4).getInteger().intValue(), is(MIN_QUORUM));

        //////////////////////////////////////
        // Test CreateProposal event values //
        //////////////////////////////////////
        NeoApplicationLog.Execution.Notification ntf = neow3j.getApplicationLog(proposalCreationTx)
                .send().getApplicationLog().getExecutions().get(0).getNotifications().get(0);
        assertThat(ntf.getEventName(), is(PROPOSAL_CREATED));
        List<StackItem> state = ntf.getState().getList();
        assertThat(reverseHexString(state.get(0).getHexString()), is(proposalHash));
        assertThat(state.get(1).getAddress(), is(alice.getAddress()));
        // first intent
        List<StackItem> i1 = state.get(2).getList().get(0).getList();
        assertThat(i1.get(0).getAddress(), is(targetContract.toAddress()));
        assertThat(i1.get(1).getString(), is(targetMethod));
        List<StackItem> i1Params = i1.get(2).getList();
        assertThat(i1Params.get(0).getAddress(), is(targetParam1.toAddress()));
        assertThat(i1Params.get(1).getAddress(), is(targetParam2.toAddress()));
        assertThat(i1Params.get(2).getInteger().intValue(), is(targetParam3));
        assertThat(state.get(3).getString(), is(proposalDescription));
        assertThat(state.get(4).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(state.get(5).getInteger().intValue(), is(MIN_QUORUM));
    }

    @Test
    public void succeed_endorsing_with_member() {
//        int n = neow3j.getTransactionHeight(proposalCreationTx).send().getHeight().intValue();
//        assertThat(list.get(3).getInteger().intValue(), is(n + REVIEW_LENGTH));
//        assertThat(list.get(4).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH));
//        assertThat(list.get(5).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH
//                + QUEUED_LENGTH));
        fail();
    }
    @Test
    public void fail_endorsing_with_non_member() {
        fail();
    }

    @Test
    public void succeed_voting_in_voting_state() throws Throwable {
        // 1. Create proposal
        Hash256 proposalCreationTx = contract.invokeFunction(CREATE_PROPOSAL,
                        hash160(alice.getScriptHash()), array(), string("empty"))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);
        String proposalHash = reverseHexString(neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog().getExecutions().get(0).getStack().get(0).getHexString());

        // 2. Endorse

        // 3. Wait till review phase ends.

        // 4. Vote
        // 5. Check ProposalVotes

        fail();
    }

    @Test
    public void fail_voting_in_review_state() {
        fail();
    }

    @Test
    public void fail_voting_in_queued_state() {
        fail();
    }

    @Test
    public void create_proposal_with_large_description() {
        fail();
    }

//    @Test
//    @DisplayName("Succeed executing proposal")
//    public void execute_proposal_success() throws Throwable {
//        Hash256 txHash = contract.invokeFunction(EXECUTE, integer(propId))
//                .signers(AccountSigner.calledByEntry(alice))
//                .sign().send().getSendRawTransaction().getHash();
//        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
//        NeoApplicationLog log = neow3j.getApplicationLog(txHash).send().getApplicationLog();
//        assertTrue(log.getExecutions().get(0).getStack().get(0).getBoolean());
//    }
//
//    @Test
//    @DisplayName("Fail calling contract-exclusive method")
//    public void call_contract_excl_method_fail() throws Throwable {
//        NeoInvokeFunction response = contract.callInvokeFunction(CALLBACK, asList(hash160(alice),
//                integer(propId)), AccountSigner.calledByEntry(alice));
//        assertThat(response.getInvocationResult().getException(),
//                containsString("No authorization!"));
//    }

}