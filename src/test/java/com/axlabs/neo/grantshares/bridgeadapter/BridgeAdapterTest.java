package com.axlabs.neo.grantshares.bridgeadapter;

import com.axlabs.neo.grantshares.GrantSharesBridgeAdapter;
import com.axlabs.neo.grantshares.GrantSharesGov;
import com.axlabs.neo.grantshares.GrantSharesTreasury;
import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.contracts.TestBridgeV2;
import com.axlabs.neo.grantshares.util.contracts.TestBridgeV3;
import io.neow3j.contract.FungibleToken;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.ContractState;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.serialization.exceptions.DeserializationException;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.transaction.exceptions.TransactionConfigurationException;
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

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.DENISE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.EVE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.FLORIAN;
import static io.neow3j.protocol.ObjectMapperFactory.getObjectMapper;
import static io.neow3j.transaction.AccountSigner.calledByEntry;
import static io.neow3j.transaction.AccountSigner.global;
import static io.neow3j.transaction.AccountSigner.none;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.byteArrayFromString;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ContractTest(
        contracts = {GrantSharesBridgeAdapter.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BridgeAdapterTest {

    private static final BigInteger DEFAULT_BRIDGE_FEE = FungibleToken.toFractions(new BigDecimal("0.1"), 8);

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    static Neow3j neow3j;
    static Account owner;
    static Hash160 whitelistedFunder;
    static SmartContract bridgeAdapter;
    static Account alice; // Set to be a DAO member.
    static Account bob;
    static Account charlie; // Set to be a DAO member.
    static Account denise; // Set to be a DAO member.
    static Account eve; // Set to be a DAO member.
    static Account florian; // Set to be a DAO member.

    // region deploy configuration

    @DeployConfig(GrantSharesBridgeAdapter.class)
    public static DeployConfiguration deployConfigBridgeAdapter(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();

        Hash160 govContractHash = new Hash160("0x2980dba54983777175780c59f64c59d2fbd5df48");
        Hash160 treasuryContractHash = new Hash160("0x95434943e07ab980dd9fbe4edc0edefb17a13517");
        Hash160 bridgeContractHash = new Hash160("0x0fbd890eb881e899fa451e561e3fef4c67184357");

        Account initialOwner = ext.getAccount(ALICE);
        config.setDeployParam(array(initialOwner, govContractHash, treasuryContractHash, bridgeContractHash,
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
    }

    // endregion setup
    // region bridge function unauthorized

    @Test
    @Order(10)
    public void failInvokeBridgeFunctionIfNotGovContract() {
        TransactionBuilder b = bridgeAdapter.invokeFunction("bridge", hash160(GasToken.SCRIPT_HASH), hash160(bob),
                integer(1)
        ).signers(none(owner));

        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("only GrantSharesGov contract"));
    }

    // endregion bridge function unauthorized
    // region nep-17 transfer

    @Test
    @Order(0)
    public void whitelistedFunderIsAllowedToSendGas() throws Throwable {
        GasToken gasToken = new GasToken(neow3j);
        BigInteger adapterBalanceBefore = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        BigInteger amount = BigInteger.TEN;

        Account whitelistedFunderAcc = ext.getAccount(whitelistedFunder.toAddress());
        NeoSendRawTransaction response = gasToken.transfer(whitelistedFunderAcc, bridgeAdapter.getScriptHash(), amount)
                .sign().send();
        assertFalse(response.hasError());
        Hash256 txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        BigInteger adapterBalanceAfter = gasToken.getBalanceOf(bridgeAdapter.getScriptHash());
        assertThat(adapterBalanceAfter, is(adapterBalanceBefore.add(amount)));
    }

    @Test
    @Order(0)
    public void whitelistedFunderIsNotAllowedToTransferTokenOtherThanGas() throws Throwable {
        NeoToken neoToken = new NeoToken(neow3j);

        Account whitelistedFunderAcc = ext.getAccount(whitelistedFunder.toAddress());
        TransactionBuilder b = neoToken.transfer(whitelistedFunderAcc, bridgeAdapter.getScriptHash(), BigInteger.TEN);
        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);

        assertThat(thrown.getMessage(), containsString("only treasury"));
    }

    // endregion nep-17 transfer
    // region test setters

    @Test
    @Order(10)
    public void setWhitelistedFunder() throws Throwable {
        Hash160 newWhitelistedFunder = eve.getScriptHash();
        assertThat(bridgeAdapter.callFunctionReturningScriptHash("whitelistedFunder"), is(whitelistedFunder));

        NeoSendRawTransaction response = bridgeAdapter.invokeFunction("setWhitelistedFunder",
                hash160(newWhitelistedFunder)
        ).signers(calledByEntry(owner)).sign().send();

        assertFalse(response.hasError());
        Hash256 txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        assertThat(bridgeAdapter.callFunctionReturningScriptHash("whitelistedFunder"), is(newWhitelistedFunder));

        // Reset to default
        response = bridgeAdapter.invokeFunction("setWhitelistedFunder", hash160(whitelistedFunder))
                .signers(calledByEntry(owner))
                .sign().send();
        assertFalse(response.hasError());
        txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    @Test
    @Order(10)
    public void failSetWhitelistedFunder_unauthorized() {
        TransactionBuilder b = bridgeAdapter.invokeFunction("setWhitelistedFunder",
                hash160(eve.getScriptHash())
        ).signers(calledByEntry(bob));

        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("only owner"));
    }

    @Test
    @Order(10)
    public void failSetWhitelistedFunder_invalid() {
        TransactionBuilder b = bridgeAdapter.invokeFunction("setWhitelistedFunder", byteArrayFromString("hello"))
                .signers(calledByEntry(owner));
        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("invalid funder"));


        b = bridgeAdapter.invokeFunction("setWhitelistedFunder", any(null))
                .signers(calledByEntry(owner));
        thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("invalid funder"));
    }

    @Test
    @Order(10)
    public void setMaxFee() throws Throwable {
        int newFee = 1;
        assertThat(bridgeAdapter.callFunctionReturningInt("maxFee"), is(DEFAULT_BRIDGE_FEE));
        assertThat(DEFAULT_BRIDGE_FEE, is(not(newFee)));

        NeoSendRawTransaction response = bridgeAdapter.invokeFunction("setMaxFee", integer(1))
                .signers(calledByEntry(owner)).sign().send();
        assertFalse(response.hasError());
        Hash256 txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        assertThat(bridgeAdapter.callFunctionReturningInt("maxFee"), is(BigInteger.valueOf(newFee)));

        // Reset to default
        response = bridgeAdapter.invokeFunction("setMaxFee", integer(DEFAULT_BRIDGE_FEE)).signers(calledByEntry(owner))
                .sign().send();
        assertFalse(response.hasError());
        txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    @Test
    @Order(10)
    public void failSetMaxFee_unauthorized() {
        TransactionBuilder b = bridgeAdapter.invokeFunction("setMaxFee", integer(1)).signers(none(bob));
        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("only owner"));
    }

    @Test
    @Order(10)
    public void failSetMaxFee_negative() {
        TransactionBuilder b = bridgeAdapter.invokeFunction("setMaxFee", integer(-1)).signers(calledByEntry(owner));
        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("invalid max fee"));

        b = bridgeAdapter.invokeFunction("setMaxFee", any(null)).signers(calledByEntry(owner));
        thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("invalid max fee"));
    }

    // endregion test setters
    // region test update

    @Test
    @Order(99)
    public void testUpdate_notAuthorized() throws Throwable {
        TransactionBuilder b = updateTxBuilder().signers(calledByEntry(bob));
        TransactionConfigurationException thrown = assertThrows(TransactionConfigurationException.class, b::sign);
        assertThat(thrown.getMessage(), containsString("only owner"));
    }

    @Test
    @Order(100)
    public void testUpdate() throws Throwable {
        ContractState contractState = neow3j.getContractState(bridgeAdapter.getScriptHash()).send().getContractState();
        assertThat(contractState.getUpdateCounter(), is(0));
        assertThat(contractState.getNef().getChecksum(), is(2619462457L));

        NeoSendRawTransaction response = updateTxBuilder().signers(calledByEntry(alice)).sign().send();
        assertFalse(response.hasError());
        Hash256 txHash = response.getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(txHash, neow3j);

        ContractState contractStateAfterUpdate = neow3j.getContractState(bridgeAdapter.getScriptHash()).send()
                .getContractState();
        assertThat(contractStateAfterUpdate.getUpdateCounter(), is(1));
        assertThat(contractStateAfterUpdate.getNef().getChecksum(), is(4241155401L));
    }

    private TransactionBuilder updateTxBuilder() throws URISyntaxException, IOException, DeserializationException {
        Path testNefFile = Paths.get("TestGrantSharesBridgeAdapter.nef");
        Path testManifestFile = Paths.get("TestGrantSharesBridgeAdapter.manifest.json");

        File nefFile = new File(getClass().getClassLoader().getResource(testNefFile.toString()).toURI());
        ContractParameter nefParam = byteArray(NefFile.readFromFile(nefFile).toArray());

        File manifestFile = new File(getClass().getClassLoader().getResource(testManifestFile.toString()).toURI());
        ContractManifest manifest = getObjectMapper().readValue(manifestFile, ContractManifest.class);
        ContractParameter manifestParam = byteArray(getObjectMapper().writeValueAsBytes(manifest));

        return bridgeAdapter.invokeFunction("update", nefParam, manifestParam, any(null));
    }

    // endregion test update

}
