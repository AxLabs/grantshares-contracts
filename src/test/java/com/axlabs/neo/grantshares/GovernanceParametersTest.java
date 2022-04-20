package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.IntentParam;
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
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.EXPIRATION_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_QUORUM;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_QUORUM_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.MULTI_SIG_THRESHOLD_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.MULTI_SIG_THRESHOLD_RATIO;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.GET_PARAMETER;
import static com.axlabs.neo.grantshares.util.TestHelper.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.PARAMETER_CHANGED;
import static com.axlabs.neo.grantshares.util.TestHelper.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.util.TestHelper.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.TIMELOCK_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.VOTING_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class GovernanceParametersTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static GrantSharesGovContract gov;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.

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
    }

    @Test
    public void get_parameter() throws IOException {
        assertThat(gov.getParameter(REVIEW_LENGTH_KEY).getInteger().intValue(), is(PHASE_LENGTH));
    }

    @Test
    public void get_parameters() throws IOException {
        Map<String, BigInteger> params = gov.getParameters();
        assertThat(params.get(REVIEW_LENGTH_KEY).intValue(), is(PHASE_LENGTH));
        assertThat(params.get(VOTING_LENGTH_KEY).intValue(), is(PHASE_LENGTH));
        assertThat(params.get(TIMELOCK_LENGTH_KEY).intValue(), is(PHASE_LENGTH));
        assertThat(params.get(EXPIRATION_LENGTH_KEY).intValue(), is(PHASE_LENGTH));
        assertThat(params.get(MIN_ACCEPTANCE_RATE_KEY).intValue(), is(MIN_ACCEPTANCE_RATE));
        assertThat(params.get(MIN_QUORUM_KEY).intValue(), is(MIN_QUORUM));
        assertThat(params.get(MULTI_SIG_THRESHOLD_KEY).intValue(), is(MULTI_SIG_THRESHOLD_RATIO));
    }

    //region CHANGE PARAMETER
    @Test
    public void execute_change_parameter() throws Throwable {
        int newValue = 60;
        IntentParam intent = IntentParam.changeParamProposal(gov.getScriptHash(), MIN_ACCEPTANCE_RATE_KEY, newValue);
        String offchainUri = "execute_change_parameter";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id).signers(AccountSigner.calledByEntry(bob))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        List<StackItem> returnVals = execution.getStack().get(0).getList();
        assertThat(returnVals.get(0).getValue(), is(nullValue()));
        assertThat(execution.getNotifications().get(0).getEventName(), is(PARAMETER_CHANGED));
        assertThat(execution.getNotifications().get(0).getContract(), is(gov.getScriptHash()));
        List<StackItem> state = execution.getNotifications().get(0).getState().getList();
        assertThat(state.get(0).getString(), is(MIN_ACCEPTANCE_RATE_KEY));
        assertThat(state.get(1).getInteger().intValue(), is(newValue));
        assertThat(execution.getNotifications().get(1).getEventName(), is(PROPOSAL_EXECUTED));
        assertThat(execution.getNotifications().get(1).getContract(), is(gov.getScriptHash()));
        assertThat(execution.getNotifications().get(1).getState().getList().get(0)
                .getInteger().intValue(), is(id));

        assertThat(gov.getParameter(MIN_ACCEPTANCE_RATE_KEY).getInteger().intValue(), is(newValue));
    }

    @Test
    public void fail_calling_change_parameter_directly() throws IOException {
        String exception = gov.callInvokeFunction(CHANGE_PARAM,
                asList(string(REVIEW_LENGTH_KEY), integer(100)),
                AccountSigner.calledByEntry(alice)).getInvocationResult().getException();

        assertThat(exception, containsString("Method only callable by the contract itself"));
    }

    @Test
    public void fail_changing_unknown_parameter() throws Throwable {
        IntentParam intent = IntentParam.changeParamProposal(gov.getScriptHash(), "unknown_param", 10);
        String offchainUri = "fail_changing_unknown_parameter";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);

        String exception = gov.execute(id).signers(AccountSigner.calledByEntry(bob)).callInvokeScript()
                .getInvocationResult().getException();
        assertThat(exception, containsString("Unknown parameter"));
    }
    //endregion CHANGE PARAMETER

}
