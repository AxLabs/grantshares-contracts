package com.axlabs.neo.grantshares.bridgeadapter;

import com.axlabs.neo.grantshares.GrantSharesBridgeAdapter;
import com.axlabs.neo.grantshares.GrantSharesGov;
import com.axlabs.neo.grantshares.GrantSharesTreasury;
import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.TestHelper;
import com.axlabs.neo.grantshares.util.contracts.TestBridgeV2;
import com.axlabs.neo.grantshares.util.contracts.TestBridgeV3;
import com.axlabs.neo.grantshares.util.proposal.ProposalBuilder;
import com.axlabs.neo.grantshares.util.proposal.ProposalData;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.ContractSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.transaction.witnessrule.AndCondition;
import io.neow3j.transaction.witnessrule.CalledByContractCondition;
import io.neow3j.transaction.witnessrule.OrCondition;
import io.neow3j.transaction.witnessrule.ScriptHashCondition;
import io.neow3j.transaction.witnessrule.WitnessAction;
import io.neow3j.transaction.witnessrule.WitnessRule;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.EXECUTE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.EVE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.FLORIAN;
import static com.axlabs.neo.grantshares.util.TestHelper.ParameterValues.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static com.axlabs.neo.grantshares.util.TestHelper.voteForProposal;
import static io.neow3j.transaction.AccountSigner.global;
import static io.neow3j.transaction.AccountSigner.none;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static io.neow3j.utils.Numeric.reverseHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;

@ContractTest(
        contracts = {GrantSharesGov.class, GrantSharesTreasury.class, TestBridgeV2.class, TestBridgeV3.class,
                GrantSharesBridgeAdapter.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BridgeAdapterTestUsingBridgeV2 {

    private static final BigInteger NEO_MAX_AMOUNT = BigInteger.valueOf(100);
    private static final BigInteger GAS_MAX_AMOUNT = FungibleToken.toFractions(BigDecimal.valueOf(10000), 8);
    private static final int MULTI_SIG_THRESHOLD_RATIO = 50;
    private static final BigInteger DEFAULT_BRIDGE_FEE = FungibleToken.toFractions(new BigDecimal("0.1"), 8);

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static Account owner;
    static Hash160 whitelistedFunder;
    static GrantSharesGovContract gov;
    static GrantSharesTreasuryContract treasury;
    static SmartContract bridgeAdapter;
    static SmartContract bridgeV2;
    static SmartContract bridgeV3;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a DAO member.
    static Account eve; // Set to be a DAO member.
    static Account florian; // Set to be a DAO member.

    // region deploy configuration

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(
                prepareDeployParameter(ext.getAccount(ALICE), ext.getAccount(CHARLIE), ext.getAccount(DENISE),
                        ext.getAccount(EVE), ext.getAccount(FLORIAN)
                ));
        return config;
    }

    @DeployConfig(GrantSharesTreasury.class)
    public static DeployConfiguration deployConfigTreasury(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        // owner
        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);

        // funders
        Account bob = ext.getAccount(BOB);
        ContractParameter funders = array(array(bob.getScriptHash(), array(bob.getECKeyPair().getPublicKey())));

        // whitelisted tokens
        Map<Hash160, BigInteger> tokens = new HashMap<>();
        tokens.put(NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT);
        tokens.put(GasToken.SCRIPT_HASH, GAS_MAX_AMOUNT);
        ContractParameter tokensParam = map(tokens);

        config.setDeployParam(array(gov.getScriptHash(), funders, tokensParam, MULTI_SIG_THRESHOLD_RATIO));
        return config;
    }

    @DeployConfig(TestBridgeV2.class)
    public static DeployConfiguration deployConfigTestBridgeV2() {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(integer(DEFAULT_BRIDGE_FEE));
        return config;
    }

    @DeployConfig(TestBridgeV3.class)
    public static DeployConfiguration deployConfigTestBridgeV3() {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(integer(DEFAULT_BRIDGE_FEE));
        return config;
    }

    @DeployConfig(GrantSharesBridgeAdapter.class)
    public static DeployConfiguration deployConfigBridgeAdapter(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();

        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);
        SmartContract treasury = ctx.getDeployedContract(GrantSharesTreasury.class);
        SmartContract bridge = ctx.getDeployedContract(TestBridgeV2.class);

        Account initialOwner = ext.getAccount(ALICE);
        config.setDeployParam(array(initialOwner, gov.getScriptHash(), treasury.getScriptHash(), bridge.getScriptHash(),
                FungibleToken.toFractions(new BigDecimal("0.1"), 8), ext.getAccount(CHARLIE)
        ));
        config.setSigner(global(initialOwner));
        return config;
    }

    // endregion deploy configuration
    // region setup

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        treasury = new GrantSharesTreasuryContract(ext.getDeployedContract(GrantSharesTreasury.class).getScriptHash(),
                neow3j
        );
        bridgeV2 = new SmartContract(ext.getDeployedContract(TestBridgeV2.class).getScriptHash(), neow3j);
        bridgeV3 = new SmartContract(ext.getDeployedContract(TestBridgeV3.class).getScriptHash(), neow3j);
        bridgeAdapter = new SmartContract(ext.getDeployedContract(GrantSharesBridgeAdapter.class).getScriptHash(),
                neow3j
        );

        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
        eve = ext.getAccount(EVE);
        florian = ext.getAccount(FLORIAN);

        owner = alice;
        whitelistedFunder = charlie.getScriptHash();

        // fund the treasury with GAS
        GasToken gasToken = new GasToken(neow3j);
        Hash256 tx = gasToken.transfer(bob, treasury.getScriptHash(), gasToken.toFractions(new BigDecimal("100")))
                .sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        // fund the treasury with NEO
        tx = new NeoToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(100)).sign().send()
                .getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);
    }

    // endregion setup
    // region fund request intents execution using adapter

    @Test
    @Order(1)
    public void execute_proposal_with_bridge_adapter_gas_to_bridge_v2() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        BigInteger bridgeFee = bridgeV2.callFunctionReturningInt("gasDepositFee");

        BigInteger treasuryBalanceBefore = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBefore = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBefore = gasToken.getBalanceOf(bridgeV2.getScriptHash());

        // Data from Neo X
        Account proposer = bob;
        Hash160 recipient = alice.getScriptHash();
        BigInteger amount = gasToken.toFractions(BigDecimal.TEN);
        String offchainUri = "execute_proposal_with_bridge_adapter_gas_v2";
        int linkedProposal = -1;

        // 1. Create proposal
        ProposalData proposalData = new ProposalData(offchainUri, linkedProposal);
        byte[] proposalScript = ProposalBuilder.buildRequestForFundsTxScriptGas(proposer, proposalData,
                gov.getScriptHash(), treasury.getScriptHash(), bridgeAdapter.getScriptHash(), recipient, amount,
                bridgeFee
        );
        TransactionBuilder b = new TransactionBuilder(neow3j).script(proposalScript);
        int id = TestHelper.sendAndEndorseProposal(gov, neow3j, proposer, alice, b);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id)).signers(none(proposer),
                ContractSigner.calledByEntry(bridgeAdapter.getScriptHash()).setRules(
                        new WitnessRule(WitnessAction.ALLOW,
                                new AndCondition(new ScriptHashCondition(gasToken.getScriptHash()),
                                        new CalledByContractCondition(bridgeV2.getScriptHash())
                                )
                        ))
        ).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        BigInteger treasuryBalanceAfter = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfter = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfter = gasToken.getBalanceOf(bridgeV2.getScriptHash());

        // Assert Balances
        assertThat(treasuryBalanceAfter, is(treasuryBalanceBefore.subtract(amount).subtract(bridgeFee)));
        assertThat(bridgeAdaptorBalanceAfter, is(bridgeAdapterBalanceBefore));
        assertThat(bridgeBalanceAfter, is(bridgeBalanceBefore.add(amount.add(bridgeFee))));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getFirstExecution();

        List<Notification> n = execution.getNotifications();
        assertThat(n, hasSize(7));

        // Intent #1: Bridge fee payment from treasury to bridge adapter.
        Notification n0 = n.get(0);
        assertThat(n0.getContract(), is(gasToken.getScriptHash()));
        assertThat(n0.getEventName(), is("Transfer"));
        assertThat(n0.getState().getList().get(0).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n0.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n0.getState().getList().get(2).getInteger(), is(bridgeFee));

        Notification n1 = n.get(1);
        assertThat(n1.getContract(), is(treasury.getScriptHash()));
        assertThat(n1.getEventName(), is("TokenReleased"));
        assertThat(n1.getState().getList().get(0).getAddress(), is(gasToken.getScriptHash().toAddress()));
        assertThat(n1.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n1.getState().getList().get(2).getInteger(), is(bridgeFee));

        // Intent #2: Transfer from bridge adapter to bridge.
        Notification n2 = n.get(2);
        assertThat(n2.getContract(), is(gasToken.getScriptHash()));
        assertThat(n2.getEventName(), is("Transfer"));
        assertThat(n2.getState().getList().get(0).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n2.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n2.getState().getList().get(2).getInteger(), is(amount));

        Notification n3 = n.get(3);
        assertThat(n3.getContract(), is(treasury.getScriptHash()));
        assertThat(n3.getEventName(), is("TokenReleased"));
        assertThat(n3.getState().getList().get(0).getAddress(), is(gasToken.getScriptHash().toAddress()));
        assertThat(n3.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n3.getState().getList().get(2).getInteger(), is(amount));

        // Intent #3: Invoke bridge adapter to bridge.
        Notification n5 = n.get(4); // Gas transfer including fee
        assertThat(n5.getContract(), is(gasToken.getScriptHash()));
        assertThat(n5.getEventName(), is("Transfer"));
        assertThat(n5.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(1).getAddress(), is(bridgeV2.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(2).getInteger(), is(amount.add(bridgeFee)));

        Notification n6 = n.get(5); // Deposit event
        assertThat(n6.getContract(), is(bridgeV2.getScriptHash()));
        assertThat(n6.getEventName(), is("GasDeposit"));
        assertThat(n6.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(1).getAddress(), is(recipient.toAddress()));
        assertThat(n6.getState().getList().get(2).getInteger(), is(amount));

        // Proposal executed event
        Notification n7 = n.get(6);
        assertThat(n7.getContract(), is(gov.getScriptHash()));
        assertThat(n7.getEventName(), is("ProposalExecuted"));
        assertThat(n7.getState().getList().get(0).getInteger(), is(BigInteger.ZERO));
    }

    @Test
    @Order(2)
    public void execute_proposal_with_bridge_adapter_neo_to_bridge_v2() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        NeoToken neoToken = new NeoToken(neow3j);
        BigInteger bridgeFee = bridgeV2.callFunctionReturningInt("tokenDepositFee", hash160(neoToken.getScriptHash()));

        BigInteger treasuryBalanceBeforeNeo = neoToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBeforeNeo = neoToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBeforeNeo = neoToken.getBalanceOf(bridgeV2.getScriptHash());

        BigInteger treasuryBalanceBeforeGas = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBeforeGas = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBeforeGas = gasToken.getBalanceOf(bridgeV2.getScriptHash());

        // Data from Neo X
        Account proposer = charlie;
        Hash160 recipient = eve.getScriptHash();
        BigInteger amount = BigInteger.TEN;
        String offchainUri = "execute_proposal_with_bridge_adapter_neo_v2";
        int linkedProposal = -1;

        // 1. Create proposal
        ProposalData proposalData = new ProposalData(offchainUri, linkedProposal);
        byte[] proposalScript = ProposalBuilder.buildRequestForFundsTxScriptNeo(proposer, proposalData,
                gov.getScriptHash(), treasury.getScriptHash(), bridgeAdapter.getScriptHash(), recipient, amount,
                bridgeFee
        );
        TransactionBuilder b = new TransactionBuilder(neow3j).script(proposalScript);
        int id = TestHelper.sendAndEndorseProposal(gov, neow3j, proposer, alice, b);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id)).signers(none(proposer),
                ContractSigner.calledByEntry(bridgeAdapter.getScriptHash()).setRules(
                        new WitnessRule(WitnessAction.ALLOW, new AndCondition(
                                new OrCondition(new ScriptHashCondition(gasToken.getScriptHash()),
                                        new ScriptHashCondition(neoToken.getScriptHash())
                                ), new CalledByContractCondition(bridgeV2.getScriptHash())
                        )
                        ))
        ).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        BigInteger treasuryBalanceAfterNeo = neoToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfterNeo = neoToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfterNeo = neoToken.getBalanceOf(bridgeV2.getScriptHash());

        BigInteger treasuryBalanceAfterGas = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfterGas = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfterGas = gasToken.getBalanceOf(bridgeV2.getScriptHash());

        // Assert Balances
        assertThat(treasuryBalanceAfterNeo, is(treasuryBalanceBeforeNeo.subtract(amount)));
        assertThat(bridgeAdaptorBalanceAfterNeo, is(bridgeAdapterBalanceBeforeNeo));
        assertThat(bridgeBalanceAfterNeo, is(bridgeBalanceBeforeNeo.add(amount)));

        assertThat(treasuryBalanceAfterGas, lessThan(treasuryBalanceBeforeGas));
        assertThat(treasuryBalanceAfterGas, greaterThan(treasuryBalanceBeforeGas.subtract(bridgeFee)));
        assertThat(bridgeAdaptorBalanceAfterGas, is(bridgeAdapterBalanceBeforeGas));
        assertThat(bridgeBalanceAfterGas, is(bridgeBalanceBeforeGas.add(bridgeFee)));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getFirstExecution();

        List<Notification> n = execution.getNotifications();
        assertThat(n, hasSize(9));

        // Intent #1: Bridge fee payment from treasury to bridge adapter.
        Notification n0 = n.get(0);
        assertThat(n0.getContract(), is(gasToken.getScriptHash()));
        assertThat(n0.getEventName(), is("Transfer"));
        assertThat(n0.getState().getList().get(0).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n0.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n0.getState().getList().get(2).getInteger(), is(bridgeFee));

        Notification n1 = n.get(1);
        assertThat(n1.getContract(), is(treasury.getScriptHash()));
        assertThat(n1.getEventName(), is("TokenReleased"));
        assertThat(n1.getState().getList().get(0).getAddress(), is(gasToken.getScriptHash().toAddress()));
        assertThat(n1.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n1.getState().getList().get(2).getInteger(), is(bridgeFee));

        // Intent #2: Transfer from bridge adapter to bridge.
        Notification n2 = n.get(2);
        assertThat(n2.getContract(), is(neoToken.getScriptHash()));
        assertThat(n2.getEventName(), is("Transfer"));
        assertThat(n2.getState().getList().get(0).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n2.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n2.getState().getList().get(2).getInteger(), is(amount));

        Notification n3 = n.get(3); // Gas reward from holding Neo
        assertThat(n3.getContract(), is(gasToken.getScriptHash()));
        assertThat(n3.getEventName(), is("Transfer"));
        assertNull(n3.getState().getList().get(0).getValue());
        assertThat(n3.getState().getList().get(1).getAddress(), is(treasury.getScriptHash().toAddress()));
        assertThat(n3.getState().getList().get(2).getInteger(), greaterThan(BigInteger.ZERO));

        Notification n4 = n.get(4);
        assertThat(n4.getContract(), is(treasury.getScriptHash()));
        assertThat(n4.getEventName(), is("TokenReleased"));
        assertThat(n4.getState().getList().get(0).getAddress(), is(neoToken.getScriptHash().toAddress()));
        assertThat(n4.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n4.getState().getList().get(2).getInteger(), is(amount));

        // Intent #3: Invoke bridge adapter to bridge.
        Notification n5 = n.get(5); // Fee transfer
        assertThat(n5.getContract(), is(gasToken.getScriptHash()));
        assertThat(n5.getEventName(), is("Transfer"));
        assertThat(n5.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(1).getAddress(), is(bridgeV2.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(2).getInteger(), is(bridgeFee));

        Notification n6 = n.get(6); // Token transfer
        assertThat(n6.getContract(), is(neoToken.getScriptHash()));
        assertThat(n6.getEventName(), is("Transfer"));
        assertThat(n6.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(1).getAddress(), is(bridgeV2.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(2).getInteger(), is(amount));

        Notification n7 = n.get(7); // Deposit event
        assertThat(n7.getContract(), is(bridgeV2.getScriptHash()));
        assertThat(n7.getEventName(), is("TokenDeposit"));
        assertThat(new Hash160(reverseHexString(n7.getState().getList().get(0).getHexString())),
                is(NeoToken.SCRIPT_HASH)
        );
        assertThat(n7.getState().getList().get(1).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n7.getState().getList().get(2).getAddress(), is(recipient.toAddress()));
        assertThat(n7.getState().getList().get(3).getInteger(), is(amount));

        // Proposal executed event
        Notification n8 = n.get(8);
        assertThat(n8.getContract(), is(gov.getScriptHash()));
        assertThat(n8.getEventName(), is("ProposalExecuted"));
        assertThat(n8.getState().getList().get(0).getInteger(), is(BigInteger.ONE));
    }

    // endregion fund request intents execution using adapter

}
