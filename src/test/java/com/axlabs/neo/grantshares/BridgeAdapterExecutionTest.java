package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.BridgeAdapterIntentHelper;
import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.TestHelper;
import com.axlabs.neo.grantshares.util.contracts.TestBridge;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.ContractSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
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
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@ContractTest(
        contracts = {GrantSharesGov.class, GrantSharesTreasury.class, TestBridge.class, GrantSharesBridgeAdapter.class},
        blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class BridgeAdapterExecutionTest {

    private static final BigInteger NEO_MAX_AMOUNT = BigInteger.valueOf(100);
    private static final BigInteger GAS_MAX_AMOUNT = FungibleToken.toFractions(BigDecimal.valueOf(10000), 8);
    private static final int MULTI_SIG_THRESHOLD_RATIO = 50;
    private static final BigInteger DEFAULT_BRIDGE_FEE = FungibleToken.toFractions(new BigDecimal("0.1"), 8);
    private static final Account backendAccount = Account.create();

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
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

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(
                ext.getAccount(ALICE), ext.getAccount(CHARLIE), ext.getAccount(DENISE), ext.getAccount(EVE),
                ext.getAccount(FLORIAN)
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

    @DeployConfig(TestBridge.class)
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
        SmartContract bridge = ctx.getDeployedContract(TestBridge.class);

        Account initialOwner = ext.getAccount(ALICE);
        config.setDeployParam(
                array(
                        initialOwner,
                        gov.getScriptHash(),
                        treasury.getScriptHash(),
                        bridge.getScriptHash(),
                        FungibleToken.toFractions(new BigDecimal("0.1"), 8),
                        backendAccount,
                        ext.getAccount(CHARLIE)
                )
        );
        config.setSigner(global(initialOwner));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        treasury = new GrantSharesTreasuryContract(
                ext.getDeployedContract(GrantSharesTreasury.class).getScriptHash(), neow3j);
        bridge = new SmartContract(ext.getDeployedContract(TestBridge.class).getScriptHash(), neow3j);
        bridgeAdapter = new SmartContract(ext.getDeployedContract(GrantSharesBridgeAdapter.class).getScriptHash(),
                neow3j
        );

        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        denise = ext.getAccount(DENISE);
        eve = ext.getAccount(EVE);
        florian = ext.getAccount(FLORIAN);

        // fund the treasury with GAS
        GasToken gasToken = new GasToken(neow3j);
        Hash256 tx = gasToken.transfer(bob, treasury.getScriptHash(), gasToken.toFractions(new BigDecimal("100")))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        // fund the treasury with NEO
        tx = new NeoToken(neow3j).transfer(bob, treasury.getScriptHash(), BigInteger.valueOf(100))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    @Test
    public void execute_proposal_with_bridge_adapter_gas() throws Throwable {
        BigInteger treasuryBalanceBefore = new GasToken(neow3j).getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdapterBalanceBefore = new GasToken(neow3j).getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceBefore = new GasToken(neow3j).getBalanceOf(bridge.getScriptHash());

        // Data from Neo X
        Hash160 token = GasToken.SCRIPT_HASH;
        Hash160 recipient = alice.getScriptHash();
        BigInteger amount = new GasToken(neow3j).toFractions(BigDecimal.TEN);
        String offchainUri = "execute_proposal_with_bridge_adapter_gas";
        int linkedProposal = -1;

        BridgeAdapterIntentHelper.FundRequestData fundRequestData = new BridgeAdapterIntentHelper.FundRequestData(token, recipient, amount,
                offchainUri, linkedProposal
        );

        // 1. Create and endorse proposal
        Account proposer = bob;

        // Build the intents for releasing Gas tokens from the treasury to the bridge adapter
        byte[] intentBytes = fundRequestData.buildIntentParamBytesForGasRequest(treasury, bridgeAdapter.getScriptHash(),
                DEFAULT_BRIDGE_FEE, recipient, amount
        );
        byte[] proposalScript = fundRequestData.buildCreateProposalScript(gov, intentBytes, proposer);

        TransactionBuilder b = new TransactionBuilder(neow3j).script(proposalScript);
        int id = TestHelper.sendAndEndorseProposal(gov, neow3j, proposer, alice, b);

        // 2. Skip to voting phase and vote
        ext.fastForwardOneBlock(PHASE_LENGTH);
        voteForProposal(gov, neow3j, id, alice);
        voteForProposal(gov, neow3j, id, charlie);
        voteForProposal(gov, neow3j, id, eve);

        // 3. Skip till after vote and queued phase, then execute.
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.invokeFunction(EXECUTE, integer(id))
                .signers(
                        AccountSigner.calledByEntry(proposer).setAllowedContracts(GasToken.SCRIPT_HASH),
                        AccountSigner.none(backendAccount).setAllowedContracts(bridgeAdapter.getScriptHash()),
                        ContractSigner.global(bridgeAdapter.getScriptHash())
                )
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        BigInteger treasuryBalanceAfter = new GasToken(neow3j).getBalanceOf(treasury.getScriptHash());
        BigInteger bridgeAdaptorBalanceAfter = new GasToken(neow3j).getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger bridgeBalanceAfter = new GasToken(neow3j).getBalanceOf(bridge.getScriptHash());

        assertThat(treasuryBalanceAfter, is(treasuryBalanceBefore.subtract(amount.add(DEFAULT_BRIDGE_FEE))));
        assertThat(bridgeAdaptorBalanceAfter, is(bridgeAdapterBalanceBefore));
        assertThat(bridgeBalanceAfter, is(bridgeBalanceBefore.add(amount.add(DEFAULT_BRIDGE_FEE))));

        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getFirstExecution();
        // Todo mialbu: Check the execution logs for the correct notifications.
    }

}
