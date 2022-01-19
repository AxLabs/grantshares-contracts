package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.TestHelper.ADD_FUNDER;
import static com.axlabs.neo.grantshares.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.TestHelper.BOB;
import static com.axlabs.neo.grantshares.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.TestHelper.DENISE;
import static com.axlabs.neo.grantshares.TestHelper.EXECUTE;
import static com.axlabs.neo.grantshares.TestHelper.GET_FUNDERS;
import static com.axlabs.neo.grantshares.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.TestHelper.REMOVE_FUNDER;
import static com.axlabs.neo.grantshares.TestHelper.createAndEndorseProposal;
import static com.axlabs.neo.grantshares.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.TestHelper.voteForProposal;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
import static io.neow3j.types.ContractParameter.publicKey;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = {GrantSharesGov.class, GrantSharesTreasury.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesTreasuryTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static SmartContract gov;
    static SmartContract treasury;
    static Account alice; // Set to be a DAO member.
    static Account bob; // Set to be a funder.
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a funder.

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfigGov() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE)));
        return config;
    }

    @DeployConfig(GrantSharesTreasury.class)
    public static DeployConfiguration deployConfigTreasury(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        // owner
        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);
        // funders
        ContractParameter funders = array(
                ext.getAccount(BOB).getECKeyPair().getPublicKey().getEncoded(true),
                ext.getAccount(DENISE).getECKeyPair().getPublicKey().getEncoded(true));
        // whitelisted tokens
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(NeoToken.SCRIPT_HASH, 100);
        tokens.put(GasToken.SCRIPT_HASH, 10000);
        ContractParameter tokensParam = map(tokens);

        config.setDeployParam(array(gov.getScriptHash(), funders, tokensParam));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = ext.getDeployedContract(GrantSharesGov.class);
        treasury = ext.getDeployedContract(GrantSharesTreasury.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
    }

    @Test
    @Order(1)
    public void execute_proposal_with_add_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_FUNDER,
                        array(publicKey(alice.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "execute_proposal_with_add_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, charlie, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
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
        assertThat(n.getEventName(), is("FunderAdded"));

        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();

        assertThat(funders.size(), is(3));
    }

    @Test
    @Order(2)
    public void fail_adding_already_added_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), ADD_FUNDER,
                        array(publicKey(ext.getAccount(DENISE).getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "fail_adding_already_added_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Already a funder"));
    }

    @Test
    @Order(3)
    public void execute_proposal_with_remove_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), REMOVE_FUNDER, array(publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "execute_proposal_with_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0);
        NeoApplicationLog.Execution.Notification n = execution.getNotifications().get(0);
        assertThat(n.getEventName(), is("FunderRemoved"));

        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();

        assertThat(funders.size(), is(2));
    }

    @Test
    @Order(4)
    public void fail_removing_nonexistant_funder() throws Throwable {
        ContractParameter intents = array(
                array(treasury.getScriptHash(), REMOVE_FUNDER,
                        array(publicKey(charlie.getECKeyPair().getPublicKey().getEncoded(true)))));
        String desc = "fail_execution_remove_funder";

        // 1. Create and endorse proposal
        int id = createAndEndorseProposal(gov, neow3j, bob, alice, intents, desc);

        // 2. Skip to voting phase and vote
        ext.fastForward(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        String exception = gov.invokeFunction(EXECUTE, integer(id))
                .signers(AccountSigner.calledByEntry(alice))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not a funder"));
    }

    @Test
    public void fail_calling_add_funder_directly() throws Exception {
        String exception = treasury.invokeFunction(ADD_FUNDER, ContractParameter.publicKey(
                        alice.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void fail_calling_remove_funder_directly() throws Exception {
        String exception = treasury.invokeFunction(REMOVE_FUNDER, ContractParameter.publicKey(
                        charlie.getECKeyPair().getPublicKey().getEncoded(true)))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("GrantSharesTreasury: Not authorised"));
    }

    @Test
    public void getFunders() throws IOException {
        List<StackItem> funders = treasury.callInvokeFunction(GET_FUNDERS).getInvocationResult()
                .getStack().get(0).getList();
        assertThat(funders.size(), is(2));
        List<byte[]> pubKeys = funders.stream()
                .map(StackItem::getByteArray)
                .collect(Collectors.toList());
        assertThat(pubKeys, containsInAnyOrder(bob.getECKeyPair().getPublicKey().getEncoded(true),
                denise.getECKeyPair().getPublicKey().getEncoded(true)));
    }

}