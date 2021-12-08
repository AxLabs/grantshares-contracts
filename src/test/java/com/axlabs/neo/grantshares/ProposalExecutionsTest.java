package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.List;

import static com.axlabs.neo.grantshares.TestConstants.ALICE;
import static com.axlabs.neo.grantshares.TestConstants.BOB;
import static com.axlabs.neo.grantshares.TestConstants.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.TestConstants.CHARLIE;
import static com.axlabs.neo.grantshares.TestConstants.CREATE;
import static com.axlabs.neo.grantshares.TestConstants.ENDORSE;
import static com.axlabs.neo.grantshares.TestConstants.EXECUTE;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM_KEY;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.VOTE;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH_KEY;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class ProposalExecutionsTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract contract;
    private static Account alice; // Set to be a DAO member.
    private static Account bob;
    private static Account charlie; // Set to be a DAO member.
    static MessageDigest hasher;

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
    }

    @Test
    public void fail_executing_non_existent_proposal() throws IOException {
        ContractParameter intents = array(
                array(
                        contract.getScriptHash(),
                        "changeParam",
                        array(MIN_ACCEPTANCE_RATE, 60)
                )
        );
        ContractParameter descHash =
                byteArray(hasher.digest("fail_executing_non_existent_proposal".getBytes(UTF_8)));
        String exception = contract.callInvokeFunction("execute", asList(intents, descHash))
                .getInvocationResult().getException();
        assertThat(exception, containsString("Proposal doesn't exist"));
    }

    @Test
    public void fail_executing_proposal_that_wasnt_endorsed() throws Throwable {
        ContractParameter intents = array(array(contract.getScriptHash(), "changeParam",
                array(MIN_ACCEPTANCE_RATE_KEY, 51)));
        String desc = "fail_executing_proposal_that_wasnt_endorsed";

        // 1. Create proposal then skip till after the queued phase without endorsing.
        Hash256 tx = contract.invokeFunction(CREATE, hash160(bob),
                        intents, byteArray(hasher.digest(desc.getBytes(UTF_8))), any(null))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        ext.fastForward(REVIEW_LENGTH + VOTING_LENGTH + QUEUED_LENGTH);

        // 2. Call execute
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        String exception = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal wasn't endorsed yet"));
    }

    @Test
    public void fail_executing_proposal_without_votes() throws Throwable {
        int newValue = 60;
        ContractParameter intents = array(array(contract.getScriptHash(), "changeParam",
                array(MIN_ACCEPTANCE_RATE_KEY, newValue)));
        String desc = "fail_executing_proposal_without_votes";

        // 1. Create and endorse proposal, then skip till after the queued phase without voting.
        String proposalHash = createAndEndorseProposal(bob, alice, intents, desc);
        ext.fastForward(REVIEW_LENGTH + VOTING_LENGTH + QUEUED_LENGTH);

        // 2. Call execute
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        String exception = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(bob))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Proposal was not handled"));
    }

    @Test
    public void execute_change_parameter() throws Throwable {
        int newValue = 60;
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                "changeParam",
                array(MIN_ACCEPTANCE_RATE_KEY, newValue)));
        String desc = "execute_change_parameter";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(proposalHash, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        Hash256 tx = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        List<StackItem> returnVals = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getList();
        assertThat(returnVals.get(0).getValue(), is(nullValue()));

        int v = contract.callInvokeFunction("getParameter", asList(string(MIN_ACCEPTANCE_RATE_KEY)))
                .getInvocationResult().getStack().get(0).getInteger().intValue();
        assertThat(v, is(newValue));
    }

    @Test
    public void fail_calling_change_parameter_directly() throws IOException {
        String exception = contract.callInvokeFunction(CHANGE_PARAM,
                asList(string(REVIEW_LENGTH_KEY), integer(100)),
                AccountSigner.calledByEntry(alice)).getInvocationResult().getException();

        assertThat(exception, containsString("Method only callable by the DAO itself"));
    }

    private String createAndEndorseProposal(Account proposer, Account endorserAndVoter,
            ContractParameter intents, String description) throws Throwable {

        // 1. create proposal
        Hash256 tx = contract.invokeFunction(CREATE, hash160(proposer),
                        intents, byteArray(hasher.digest(description.getBytes(UTF_8))), any(null))
                .signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        String proposalHash = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getHexString();

        // 2. endorse proposal
        tx = contract.invokeFunction(ENDORSE, byteArray(proposalHash), hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        return proposalHash;
    }

    private String voteForProposal(String proposalHash, Account endorserAndVoter) throws Throwable {
        Hash256 tx = contract.invokeFunction(VOTE, byteArray(proposalHash), integer(1),
                        hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        return proposalHash;
    }
}
