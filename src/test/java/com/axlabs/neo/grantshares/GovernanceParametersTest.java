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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.TestHelper.ADD_MEMBER;
import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.GET_MEMBERS;
import static com.axlabs.neo.grantshares.TestHelper.GET_PARAMETER;
import static com.axlabs.neo.grantshares.TestHelper.MEMBER_ADDED;
import static com.axlabs.neo.grantshares.TestHelper.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.TestHelper.PARAMETER_CHANGED;
import static com.axlabs.neo.grantshares.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.TestHelper.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.REMOVE_MEMBER;
import static com.axlabs.neo.grantshares.TestHelper.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestHelper.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.hasher;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class GovernanceParametersTest {

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

    @Test
    public void get_parameters() throws IOException {
        assertThat(contract.callInvokeFunction(GET_PARAMETER, asList(string(REVIEW_LENGTH_KEY)))
                        .getInvocationResult().getStack().get(0).getInteger().intValue(),
                is(REVIEW_LENGTH));
    }

    //region CHANGE PARAMETER
    @Test
    public void execute_change_parameter() throws Throwable {
        int newValue = 60;
        ContractParameter intents = array(array(
                contract.getScriptHash(),
                CHANGE_PARAM,
                array(MIN_ACCEPTANCE_RATE_KEY, newValue)));
        String desc = "execute_change_parameter";

        // 1. Create and endorse proposal
        String proposalHash = createAndEndorseProposal(contract, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(REVIEW_LENGTH);
        voteForProposal(contract, neow3j, proposalHash, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(VOTING_LENGTH + QUEUED_LENGTH);
        byte[] descHash = hasher.digest(desc.getBytes(UTF_8));
        Hash256 tx = contract.invokeFunction(EXECUTE, intents, byteArray(descHash))
                .signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        List<StackItem> returnVals = execution.getStack().get(0).getList();
        assertThat(returnVals.get(0).getValue(), is(nullValue()));
        assertThat(execution.getNotifications().get(0).getEventName(), is(PARAMETER_CHANGED));
        assertThat(execution.getNotifications().get(0).getContract(), is(contract.getScriptHash()));
        List<StackItem> state = execution.getNotifications().get(0).getState().getList();
        assertThat(state.get(0).getString(), is(MIN_ACCEPTANCE_RATE_KEY));
        assertThat(state.get(1).getInteger().intValue(), is(newValue));
        assertThat(execution.getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));
        assertThat(execution.getNotifications().get(1).getContract(), is(contract.getScriptHash()));
        assertThat(execution.getNotifications().get(1).getState().getList().get(0).getHexString(),
                is(proposalHash));

        int v = contract.callInvokeFunction(GET_PARAMETER,
                        asList(string(MIN_ACCEPTANCE_RATE_KEY)))
                .getInvocationResult().getStack().get(0).getInteger().intValue();
        assertThat(v, is(newValue));
    }

    @Test
    public void fail_calling_change_parameter_directly() throws IOException {
        String exception = contract.callInvokeFunction(CHANGE_PARAM,
                asList(string(REVIEW_LENGTH_KEY), integer(100)),
                AccountSigner.calledByEntry(alice)).getInvocationResult().getException();

        assertThat(exception, containsString("Method only callable by the contract itself"));
    }
    //endregion CHANGE PARAMETER

}
