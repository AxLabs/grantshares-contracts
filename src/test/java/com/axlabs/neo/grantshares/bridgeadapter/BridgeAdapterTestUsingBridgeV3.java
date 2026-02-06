package com.axlabs.neo.grantshares.bridgeadapter;

import com.axlabs.neo.grantshares.GrantSharesBridgeAdapter;
import com.axlabs.neo.grantshares.GrantSharesGov;
import com.axlabs.neo.grantshares.GrantSharesTreasury;
import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.TestHelper;
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
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
import io.neow3j.transaction.witnessrule.AndCondition;
import io.neow3j.transaction.witnessrule.CalledByContractCondition;
import io.neow3j.transaction.witnessrule.OrCondition;
import io.neow3j.transaction.witnessrule.ScriptHashCondition;
import io.neow3j.transaction.witnessrule.WitnessAction;
import io.neow3j.transaction.witnessrule.WitnessRule;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
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
import static io.neow3j.utils.Numeric.hexStringToByteArray;
import static io.neow3j.utils.Numeric.reverseHexString;
import static io.neow3j.utils.Numeric.toHexString;
import static io.neow3j.utils.Numeric.toHexStringNoPrefix;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ContractTest(
        contracts = {GrantSharesGov.class, GrantSharesTreasury.class, TestBridgeV3.class,
                GrantSharesBridgeAdapter.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BridgeAdapterTestUsingBridgeV3 {

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
    static SmartContract bridge;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a DAO member.
    static Account eve; // Set to be a DAO member.
    static Account florian; // Set to be a DAO member.

    static String bridgeAdapterHashLittleEndianHex;
    static String gsGovHashLittleEndianHex;
    static String gsTreasuryHashLittleEndianHex;

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

    @DeployConfig(TestBridgeV3.class)
    public static DeployConfiguration deployConfigTestBridge() {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(integer(DEFAULT_BRIDGE_FEE));
        return config;
    }

    @DeployConfig(GrantSharesBridgeAdapter.class)
    public static DeployConfiguration deployConfigBridgeAdapter(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();

        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);
        SmartContract treasury = ctx.getDeployedContract(GrantSharesTreasury.class);
        SmartContract bridge = ctx.getDeployedContract(TestBridgeV3.class);

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
        bridge = new SmartContract(ext.getDeployedContract(TestBridgeV3.class).getScriptHash(), neow3j);
        bridgeAdapter = new SmartContract(ext.getDeployedContract(GrantSharesBridgeAdapter.class).getScriptHash(),
                neow3j
        );

        gsGovHashLittleEndianHex = toHexStringNoPrefix(gov.getScriptHash().toLittleEndianArray());
        gsTreasuryHashLittleEndianHex = toHexStringNoPrefix(treasury.getScriptHash().toLittleEndianArray());
        bridgeAdapterHashLittleEndianHex = toHexStringNoPrefix(bridgeAdapter.getScriptHash().toLittleEndianArray());

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
    // region fund request native deposit

    @Test
    @Order(1)
    public void execute_proposal_native_deposit() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        BigInteger bridgeFee = bridge.callFunctionReturningInt("nativeDepositFee");

        BigInteger treasuryBalanceBefore = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBefore = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBefore = gasToken.getBalanceOf(bridge.getScriptHash());

        // Data from Neo X
        Account proposer = bob;
        Hash160 recipient = alice.getScriptHash();
        BigInteger amount = gasToken.toFractions(BigDecimal.TEN);
        String offchainUri = "native_deposit";
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
                                        new CalledByContractCondition(bridge.getScriptHash())
                                )
                        ))
        ).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getFirstExecution();
        assertNull(execution.getException());
        assertThat(execution.getState(), is(NeoVMStateType.HALT));

        BigInteger treasuryBalanceAfter = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfter = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfter = gasToken.getBalanceOf(bridge.getScriptHash());

        // Assert Balances
        assertThat(treasuryBalanceAfter, is(treasuryBalanceBefore.subtract(amount).subtract(bridgeFee)));
        assertThat(bridgeAdaptorBalanceAfter, is(bridgeAdapterBalanceBefore));
        assertThat(bridgeBalanceAfter, is(bridgeBalanceBefore.add(amount.add(bridgeFee))));

        List<Notification> n = execution.getNotifications();
        assertThat(n, hasSize(8));

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
        Notification n5 = n.get(4); // Fee payment transfer
        assertThat(n5.getContract(), is(gasToken.getScriptHash()));
        assertThat(n5.getEventName(), is("Transfer"));
        assertThat(n5.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(1).getAddress(), is(bridge.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(2).getInteger(), is(bridgeFee));

        Notification n6 = n.get(5); // Gas payment
        assertThat(n6.getContract(), is(gasToken.getScriptHash()));
        assertThat(n6.getEventName(), is("Transfer"));
        assertThat(n6.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(1).getAddress(), is(bridge.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(2).getInteger(), is(amount));

        Notification n7 = n.get(6); // Deposit event
        assertThat(n7.getContract(), is(bridge.getScriptHash()));
        assertThat(n7.getEventName(), is("NativeDeposit"));
        assertThat(n7.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n7.getState().getList().get(1).getAddress(), is(recipient.toAddress()));
        assertThat(n7.getState().getList().get(2).getInteger(), is(amount));

        // Proposal executed event
        Notification n8 = n.get(7);
        assertThat(n8.getContract(), is(gov.getScriptHash()));
        assertThat(n8.getEventName(), is("ProposalExecuted"));
        assertThat(n8.getState().getList().get(0).getInteger(), is(BigInteger.ZERO));
    }

    // endregion
    // region fund request token deposit

    @Test
    @Order(2)
    public void execute_proposal_token_deposit() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        NeoToken neoToken = new NeoToken(neow3j);
        BigInteger bridgeFee = bridge.callFunctionReturningInt("tokenDepositFee", hash160(neoToken.getScriptHash()));

        BigInteger treasuryBalanceBeforeNeo = neoToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBeforeNeo = neoToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBeforeNeo = neoToken.getBalanceOf(bridge.getScriptHash());

        BigInteger treasuryBalanceBeforeGas = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBeforeGas = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBeforeGas = gasToken.getBalanceOf(bridge.getScriptHash());

        // Data from Neo X
        Account proposer = charlie;
        Hash160 recipient = eve.getScriptHash();
        BigInteger amount = BigInteger.TEN;
        String offchainUri = "token_deposit";
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
                                ), new CalledByContractCondition(bridge.getScriptHash())
                        )
                        ))
        ).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getFirstExecution();
        assertNull(execution.getException());
        assertThat(execution.getState(), is(NeoVMStateType.HALT));

        BigInteger treasuryBalanceAfterNeo = neoToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfterNeo = neoToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfterNeo = neoToken.getBalanceOf(bridge.getScriptHash());

        BigInteger treasuryBalanceAfterGas = gasToken.getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfterGas = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfterGas = gasToken.getBalanceOf(bridge.getScriptHash());

        // Assert Balances
        assertThat(treasuryBalanceAfterNeo, is(treasuryBalanceBeforeNeo.subtract(amount)));
        assertThat(bridgeAdaptorBalanceAfterNeo, is(bridgeAdapterBalanceBeforeNeo));
        assertThat(bridgeBalanceAfterNeo, is(bridgeBalanceBeforeNeo.add(amount)));

        assertThat(treasuryBalanceAfterGas, lessThan(treasuryBalanceBeforeGas));
        assertThat(treasuryBalanceAfterGas, greaterThan(treasuryBalanceBeforeGas.subtract(bridgeFee)));
        assertThat(bridgeAdaptorBalanceAfterGas, is(bridgeAdapterBalanceBeforeGas));
        assertThat(bridgeBalanceAfterGas, is(bridgeBalanceBeforeGas.add(bridgeFee)));

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
        assertThat(n5.getState().getList().get(1).getAddress(), is(bridge.getScriptHash().toAddress()));
        assertThat(n5.getState().getList().get(2).getInteger(), is(bridgeFee));

        Notification n6 = n.get(6); // Token transfer
        assertThat(n6.getContract(), is(neoToken.getScriptHash()));
        assertThat(n6.getEventName(), is("Transfer"));
        assertThat(n6.getState().getList().get(0).getAddress(), is(bridgeAdapter.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(1).getAddress(), is(bridge.getScriptHash().toAddress()));
        assertThat(n6.getState().getList().get(2).getInteger(), is(amount));

        Notification n7 = n.get(7); // Deposit event
        assertThat(n7.getContract(), is(bridge.getScriptHash()));
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
    // region balance limit tests - native deposit

    @Test
    @Order(3)
    public void execute_proposal_native_deposit_gas_remainder_at_limit() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        BigInteger bridgeFee = bridge.callFunctionReturningInt("nativeDepositFee");
        BigInteger maxFee = bridgeAdapter.callFunctionReturningInt("maxFee");

        BigInteger bridgeGasBalanceBefore = gasToken.getBalanceOf(bridge.getScriptHash());
        assertThat(gasToken.getBalanceOf(bridgeAdapter.getScriptHash()), is(BigInteger.ZERO));

        // Data from Neo X
        Account proposer = bob;
        BigInteger amount = gasToken.toFractions(BigDecimal.TEN);

        // fee = 80969800
        // fee to use in this test: bridge fee + maxFee

        // 1. Create proposal
        // This script is the same as used in the native deposit test, but with a different offchainUri, and the
        // fee amount to send to the adapter set to bridgeFee+maxFee, so that after the deposit, an amount of GAS
        // remains in the adapter that is equal the maxFee value (which is the limit of what is allowed to remain).
        // offchainUri used: "native_deposit_gas_remainder_at_limit"
        byte[] proposalScript = hexStringToByteArray(
                "0f0c256e61746976655f6465706f7369745f6761735f72656d61696e6465725f61745f6c696d69741f0200ca9a3b0c140d165c9899c38bbf5991c5e47b04937258caec690c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f0200ca9a3b0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02002d31010c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14989b16363233ccc699c6a9a1840ab8cd1934d58214c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52");
        TransactionBuilder b = new TransactionBuilder(neow3j).script(proposalScript);
        int id = TestHelper.sendAndEndorseProposal(gov, neow3j, proposer, alice, b);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 txHash = gov.invokeFunction(EXECUTE, integer(id)).signers(none(proposer),
                ContractSigner.calledByEntry(bridgeAdapter.getScriptHash()).setRules(
                        new WitnessRule(WitnessAction.ALLOW,
                                new AndCondition(new ScriptHashCondition(gasToken.getScriptHash()),
                                        new CalledByContractCondition(bridge.getScriptHash())
                                )
                        ))
        ).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        NeoApplicationLog.Execution exec = neow3j.getApplicationLog(txHash).send().getApplicationLog()
                .getFirstExecution();
        assertThat(exec.getState(), is(NeoVMStateType.HALT));
        // Transfer+TokenReleased - treasury to adapter for the fee
        // Transfer+TokenReleased - treasury to adapter for the amount
        // Transfer+Transfer+NativeDeposit - bridge fee + amount transfers + deposit event
        // ProposalExecuted - gs-gov event
        assertThat(exec.getNotifications(), hasSize(8));
        assertThat(exec.getNotifications().get(7).getEventName(), is("ProposalExecuted"));

        BigInteger bridgeGasBalanceAfter = gasToken.getBalanceOf(bridge.getScriptHash());
        // The amount to deposit is 10 GAS, so the bridge should hold the fee plus these 10 GAS more after the
        // proposal execution.
        BigInteger nativeDepositAmount = gasToken.toFractions(BigDecimal.TEN);
        assertThat(bridgeGasBalanceAfter, is(bridgeGasBalanceBefore.add(bridgeFee).add(nativeDepositAmount)));

        // Drain the remainder to clean the plate for other tests.
        BigInteger adapterGasBalance = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        assertThat(adapterGasBalance, is(maxFee)); // The GAS remainder equal to the limit, i.e., maxFee.

        Hash256 drainTx = gasToken.transfer(bridgeAdapter.getScriptHash(), alice.getScriptHash(), adapterGasBalance)
                .signers(none(alice), ContractSigner.global(bridgeAdapter.getScriptHash()))
                .sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(drainTx, neow3j);
        assertThat(gasToken.getBalanceOf(bridgeAdapter.getScriptHash()), is(BigInteger.ZERO));
    }

    @Test
    @Order(4)
    public void execute_proposal_native_deposit_gas_remainder_limit_exceeded() throws Throwable {
        BigInteger bridgeFee = bridge.callFunctionReturningInt("nativeDepositFee");
        BigInteger maxFee = bridgeAdapter.callFunctionReturningInt("maxFee");

        // Data from Neo X
        Account proposer = bob;

        // 1. Create proposal
        // This script is the same as used in the native deposit test, but with a different offchainUri, and the
        // fee amount to send to the adapter set to bridgeFee+maxFee+1, so that after the deposit, the balance check
        // for the remaining GAS balance in the adapter should abort the transaction as the remainder should not exceed
        // the allowed limit.
        // offchainUri used: "native_deposit_gas_remainder_limit_exceeded"
        byte[] proposalScript = hexStringToByteArray(
                "0f0c2b6e61746976655f6465706f7369745f6761735f72656d61696e6465725f6c696d69745f65786365656465641f0200ca9a3b0c140d165c9899c38bbf5991c5e47b04937258caec690c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f0200ca9a3b0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02" +
                        reverseHexString(toHexStringNoPrefix(bridgeFee.add(maxFee).add(BigInteger.ONE).toByteArray())) +
                        "0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14989b16363233ccc699c6a9a1840ab8cd1934d58214c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52");
        assertThat(toHexStringNoPrefix(proposalScript),
                is("0f0c2b6e61746976655f6465706f7369745f6761735f72656d61696e6465725f6c696d69745f65786365656465641f0200ca9a3b0c140d165c9899c38bbf5991c5e47b04937258caec690c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f0200ca9a3b0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02" +
                        "012d3101" +
                        "0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14989b16363233ccc699c6a9a1840ab8cd1934d58214c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52")
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
        TransactionConfigurationException thrown = assertThrows(
                TransactionConfigurationException.class, () -> gov.invokeFunction(EXECUTE, integer(id))
                        .signers(none(proposer), ContractSigner.global(bridgeAdapter.getScriptHash())).sign()
        );
        assertThat(thrown.getMessage(),
                containsString("ABORTMSG is executed. Reason: unallowed token balance remainder")
        );
    }

    // endregion
    // region balance limit tests - token deposit

    @Test
    @Order(5)
    public void execute_proposal_token_deposit_gas_remainder_at_limit() throws Throwable {
        NeoToken neoToken = new NeoToken(neow3j);
        BigInteger bridgeNeoBalanceBefore = neoToken.getBalanceOf(bridge.getScriptHash());
        boolean bridgeHeldNeoBeforeDeposit =
                bridgeNeoBalanceBefore.compareTo(BigInteger.ZERO) != 0;

        GasToken gasToken = new GasToken(neow3j);
        BigInteger bridgeFee = bridge.callFunctionReturningInt("tokenDepositFee", hash160(neoToken.getScriptHash()));
        BigInteger maxFee = bridgeAdapter.callFunctionReturningInt("maxFee");

        assertThat(gasToken.getBalanceOf(bridgeAdapter.getScriptHash()), is(BigInteger.ZERO));

        BigInteger bridgeGasBalanceBefore = gasToken.getBalanceOf(bridge.getScriptHash());

        // Data from Neo X
        Account proposer = charlie;

        // 1. Create proposal
        // This script is the same as used in the token deposit test, but with a different offchainUri, and the
        // fee amount to send to the adapter set to bridgeFee+maxFee, so that after the deposit, an amount of GAS
        // remains in the adapter that is equal the maxFee value (which is the limit of what is allowed to remain).
        // offchainUri used: "token_deposit_gas_remainder_limit_exceeded"
        byte[] proposalScript = hexStringToByteArray(
                "0x0f0c24746f6b656e5f6465706f7369745f6761735f72656d61696e6465725f61745f6c696d69741f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f1a0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02" +
                        // sending (bridge fee + maxFee) GAS to the adapter, so that the remainder will be exactly the
                        // limit. The execution should be successful in that case.
                        reverseHexString(toHexString(bridgeFee.add(maxFee).toByteArray())) +
                        "0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52"
        );
        assertThat(toHexStringNoPrefix(proposalScript),
                is("0f0c24746f6b656e5f6465706f7369745f6761735f72656d61696e6465725f61745f6c696d69741f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f1a0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02002d31010c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52")
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
        Hash256 txHash = gov.invokeFunction(EXECUTE, integer(id))
                .signers(none(proposer), ContractSigner.global(bridgeAdapter.getScriptHash()))
                .sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        NeoApplicationLog.Execution exec = neow3j.getApplicationLog(txHash).send().getApplicationLog()
                .getFirstExecution();
        assertThat(exec.getState(), is(NeoVMStateType.HALT));

        // Transfer+TokenReleased - treasury to adapter for the fee
        // Transfer+Transfer+TokenReleased - treasury to adapter for the amount (incl. Gas reward for treasury)
        // Transfer - bridge fee
        // Transfer(+Transfer)+TokenDeposit - neo to bridge (+ gas reward to bridge) +
        // deposit event
        // Note, there's no Gas reward for the adapter since NEO has to be held at least for 1 block for GAS rewards.
        // ProposalExecuted - gs-gov event
        int nrEvents;
        if (bridgeHeldNeoBeforeDeposit) {
            nrEvents = 10;
        } else {
            nrEvents = 9;
        }
        assertThat(exec.getNotifications(), hasSize(nrEvents));
        assertThat(exec.getNotifications().get(nrEvents - 1).getEventName(), is("ProposalExecuted"));

        BigInteger bridgeGasBalanceAfter = gasToken.getBalanceOf(bridge.getScriptHash());
        // greater than because it might received GAS rewards
        assertThat(bridgeGasBalanceAfter, greaterThan(bridgeGasBalanceBefore.add(bridgeFee)));
        BigInteger bridgeNeoBalanceAfter = neoToken.getBalanceOf(bridge.getScriptHash());
        assertThat(bridgeNeoBalanceAfter, is(bridgeNeoBalanceBefore.add(BigInteger.TEN)));

        // Drain the remainder to clean the plate for other tests.
        BigInteger adapterGasBalance = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        assertThat(adapterGasBalance, is(maxFee)); // The GAS remainder equal to the limit, i.e., maxFee.

        Hash256 drainTx = gasToken.transfer(bridgeAdapter.getScriptHash(), alice.getScriptHash(), adapterGasBalance)
                .signers(none(alice), ContractSigner.global(bridgeAdapter.getScriptHash()))
                .sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(drainTx, neow3j);
        assertThat(gasToken.getBalanceOf(bridgeAdapter.getScriptHash()), is(BigInteger.ZERO));
    }

    @Test
    @Order(6)
    public void execute_proposal_token_deposit_gas_remainder_limit_exceeded() throws Throwable {
        NeoToken neoToken = new NeoToken(neow3j);
        BigInteger bridgeFee = bridge.callFunctionReturningInt("tokenDepositFee", hash160(neoToken.getScriptHash()));
        BigInteger adaptersMaxFee = bridgeAdapter.callFunctionReturningInt("maxFee");

        // Data from Neo X
        Account proposer = charlie;

        // 1. Create proposal
        // This script is the same as used in the token deposit test, but with a different offchainUri, and the
        // bridge fee set to bridgeFee+maxFee+1, so that after the deposit, the balance check for the remaining GAS
        // balance in the adapter should abort the transaction as the remainder should not exceed the allowed limit.
        // offchainUri: "token_deposit_gas_remainder_limit_exceeded"
        byte[] proposalScript = hexStringToByteArray(
                "0f0c2a746f6b656e5f6465706f7369745f6761735f72656d61696e6465725f6c696d69745f65786365656465641f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f1a0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02" +
                        // sending (bridge fee + maxFee + 1) GAS to the adapter, so that the remainder will exceed
                        // the limit by 1. The execution should fail and the tx should abort.
                        reverseHexString(toHexString(bridgeFee.add(adaptersMaxFee).add(BigInteger.ONE).toByteArray())) +
                        "0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52"
        );
        assertThat(toHexStringNoPrefix(proposalScript),
                is("0f0c2a746f6b656e5f6465706f7369745f6761735f72656d61696e6465725f6c696d69745f65786365656465641f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f1a0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02" +
                        "012d3101" +
                        "0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52")
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
        TransactionConfigurationException thrown = assertThrows(
                TransactionConfigurationException.class, () -> gov.invokeFunction(EXECUTE, integer(id))
                        .signers(none(proposer), ContractSigner.global(bridgeAdapter.getScriptHash())).sign()
        );
        assertThat(thrown.getMessage(),
                containsString("ABORTMSG is executed. Reason: unallowed token balance remainder")
        );
    }

    @Test
    @Order(7)
    public void execute_proposal_token_deposit_remaining_tokens() throws Throwable {
        // Data from Neo X
        Account proposer = charlie;
        BigInteger amount = BigInteger.TEN; // That's the amount used for the bridge() function in the script below.

        // 1. Create proposal
        // This script is the same as used in the token deposit test, but with a different offchainUri, and the
        // amount to release from the treasury to the amount+1, so that after the deposit, the balance check for the
        // remaining NEO balance in the adapter should abort the transaction as the remainder should not exceed the
        // allowed limit which is 0 for non-GAS tokens.
        // offchainUri: "token_deposit_remaining_tokens"
        byte[] proposalScript = hexStringToByteArray(
                "0x0f0c1e746f6b656e5f6465706f7369745f72656d61696e696e675f746f6b656e731f1a0c14905" +
                        reverseHexString(toHexString(amount.add(BigInteger.ONE).toByteArray())) +
                        "f308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "14c01f1b0c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c01f02809698000c14" +
                        bridgeAdapterHashLittleEndianHex +
                        "0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c14" +
                        gsTreasuryHashLittleEndianHex +
                        "14c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c14" +
                        gsGovHashLittleEndianHex +
                        "41627d5b52");

        TransactionBuilder b = new TransactionBuilder(neow3j).script(proposalScript);
        int id = TestHelper.sendAndEndorseProposal(gov, neow3j, proposer, alice, b);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        TransactionConfigurationException thrown = assertThrows(
                TransactionConfigurationException.class, () -> gov.invokeFunction(EXECUTE, integer(id))
                        .signers(none(proposer), ContractSigner.global(bridgeAdapter.getScriptHash())).sign()
        );
        assertThat(thrown.getMessage(),
                containsString("ABORTMSG is executed. Reason: unallowed token balance remainder")
        );
    }

    // endregion

}
