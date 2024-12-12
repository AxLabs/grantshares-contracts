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
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
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

import static com.axlabs.neo.grantshares.util.TestHelper.Events.PARAMETER_CHANGED;
import static com.axlabs.neo.grantshares.util.TestHelper.Events.PROPOSAL_EXECUTED;
import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.CHANGE_PARAM;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.EXPIRATION_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.MIN_QUORUM_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.MULTI_SIG_THRESHOLD_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.TIMELOCK_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterNames.VOTING_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.MIN_QUORUM;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.MULTI_SIG_THRESHOLD_RATIO;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertThat(gov.getParameter(REVIEW_LENGTH_KEY).getInteger().intValue(), is(PHASE_LENGTH * 1000));
    }

    @Test
    public void get_parameters() throws IOException {
        Map<String, BigInteger> params = gov.getParameters();
        assertThat(params.get(REVIEW_LENGTH_KEY).intValue(), is(PHASE_LENGTH * 1000));
        assertThat(params.get(VOTING_LENGTH_KEY).intValue(), is(PHASE_LENGTH * 1000));
        assertThat(params.get(TIMELOCK_LENGTH_KEY).intValue(), is(PHASE_LENGTH * 1000));
        assertThat(params.get(EXPIRATION_LENGTH_KEY).intValue(), is(PHASE_LENGTH * 1000));
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
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
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
    public void fail_calling_change_parameter_directly() {
        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.invokeFunction(CHANGE_PARAM, string(REVIEW_LENGTH_KEY), integer(100)).signers(
                        AccountSigner.calledByEntry(alice)).sign());
        assertTrue(e.getMessage().endsWith("Method only callable by the contract itself"));
    }

    @Test
    public void fail_changing_unknown_parameter() throws Throwable {
        IntentParam intent = IntentParam.changeParamProposal(gov.getScriptHash(), "unknown_param", 10);
        String offchainUri = "fail_changing_unknown_parameter";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(intent), offchainUri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Unknown parameter"));
    }

    @Test
    public void fail_changing_voting_length_to_negative_value() throws Throwable {
        IntentParam i = IntentParam.changeParamProposal(gov.getScriptHash(), VOTING_LENGTH_KEY, -1);
        String uri = "fail_changing_voting_length_to_negative_value";
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(i), uri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Invalid parameter value"));
        assertThat(gov.getParameter(VOTING_LENGTH_KEY).getInteger().intValue(), is(PHASE_LENGTH * 1000));
    }

    @Test
    public void fail_changing_min_acceptance_rate_to_negative_value() throws Throwable {
        ContractParameter i = IntentParam.changeParamProposal(gov.getScriptHash(), MIN_ACCEPTANCE_RATE_KEY, -1);
        String uri = "fail_changing_min_acceptance_rate_to_negative_value";
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(i), uri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Invalid parameter value"));
        assertThat(gov.getParameter(MIN_ACCEPTANCE_RATE_KEY).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
    }

    @Test
    public void fail_changing_min_acceptance_rate_to_more_than_hundred() throws Throwable {
        ContractParameter i = IntentParam.changeParamProposal(gov.getScriptHash(), MIN_ACCEPTANCE_RATE_KEY, 101);
        String uri = "fail_changing_min_acceptance_rate_to_more_than_hundred";
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(i), uri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Invalid parameter value"));
        assertThat(gov.getParameter(MIN_ACCEPTANCE_RATE_KEY).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
    }

    @Test
    public void fail_changing_multisig_threshold_to_zero() throws Throwable {
        ContractParameter i = IntentParam.changeParamProposal(gov.getScriptHash(), MULTI_SIG_THRESHOLD_KEY, 0);
        String uri = "fail_changing_multisig_threshold_to_zero";
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(i), uri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Invalid parameter value"));
        assertThat(gov.getParameter(MULTI_SIG_THRESHOLD_KEY).getInteger().intValue(), is(MULTI_SIG_THRESHOLD_RATIO));
    }

    @Test
    public void fail_changing_multisig_threshold_to_more_than_hundred() throws Throwable {
        ContractParameter i = IntentParam.changeParamProposal(gov.getScriptHash(), MULTI_SIG_THRESHOLD_KEY, 101);
        String uri = "fail_changing_multisig_threshold_to_more_than_hundred";
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, array(i), uri);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);

        TransactionConfigurationException e = assertThrows(TransactionConfigurationException.class,
                () -> gov.execute(id).signers(AccountSigner.calledByEntry(bob)).sign());
        assertTrue(e.getMessage().endsWith("Invalid parameter value"));
        assertThat(gov.getParameter(MULTI_SIG_THRESHOLD_KEY).getInteger().intValue(), is(MULTI_SIG_THRESHOLD_RATIO));
    }

    //endregion CHANGE PARAMETER

}
