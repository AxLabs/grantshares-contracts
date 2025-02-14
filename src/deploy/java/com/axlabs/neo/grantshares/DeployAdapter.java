package com.axlabs.neo.grantshares;

import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.math.BigDecimal;
import java.math.BigInteger;

import static com.axlabs.neo.grantshares.CompilationHelper.compileAndWriteNefAndManifestFiles;
import static com.axlabs.neo.grantshares.CompilationHelper.readManifest;
import static com.axlabs.neo.grantshares.CompilationHelper.readNefFile;
import static io.neow3j.transaction.AccountSigner.none;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;

public class DeployAdapter {

    // N3 testnet node
    private static final Neow3j neow3j = Neow3j.build(new HttpService("http://seed1t5.neo.org:20332"));

    // N3 testnet contracts
    private static final Hash160 GRANTSHARES_GOV_HASH = new Hash160("0x7f96d67752ac47928742bc518f528d0f42964645");
    private static final Hash160 GRANTSHARES_TREASURY_HASH = new Hash160("0xc8dc114f3986579b8f318a484ff0e5f97d120fab");
    private static final Hash160 NEOX_BRIDGE_HASH = new Hash160("0x2ba94444d43c9a084a5660982a9f95f43f07422e");

    private static final Account deployerAndInitialOwner = Account.fromWIF("");

    private static final String BRIDGE_ADAPTER_CONTRACT_NAME = "GrantSharesBridgeAdapter";

    // Make sure to fill in the required values in DeployAdapter.java before running this main method.
    public static void main(String[] args) throws Throwable {
        compileAndWriteNefAndManifestFiles(GrantSharesBridgeAdapter.class);
        deployGrantSharesBridgeAdapter();
    }

    // region deployment

    private static void deployGrantSharesBridgeAdapter() throws Throwable {
        NefFile nefFile = readNefFile(BRIDGE_ADAPTER_CONTRACT_NAME);
        ContractManifest manifest = readManifest(BRIDGE_ADAPTER_CONTRACT_NAME);

        Hash160 adapterContractHash = SmartContract.calcContractHash(
                deployerAndInitialOwner.getScriptHash(),
                nefFile.getCheckSumAsInteger(),
                manifest.getName()
        );

        Hash160 initialOwner = deployerAndInitialOwner.getScriptHash();
        // The following hashes are the contract hashes on testnet.
        BigInteger initialMaxFee = new GasToken(neow3j).toFractions(new BigDecimal("0.1"));
        Hash160 initialWhitelistedFunder = deployerAndInitialOwner.getScriptHash();

        ContractParameter deploymentParam = array(
                hash160(initialOwner),
                hash160(GRANTSHARES_GOV_HASH),
                hash160(GRANTSHARES_TREASURY_HASH),
                hash160(NEOX_BRIDGE_HASH),
                integer(initialMaxFee),
                hash160(initialWhitelistedFunder)

        );

        TransactionBuilder b = new ContractManagement(neow3j).deploy(nefFile, manifest, deploymentParam)
                .signers(none(deployerAndInitialOwner).setAllowedContracts(adapterContractHash));
        NeoSendRawTransaction response = b.sign().send();
        if (response.hasError()) {
            throw new Exception("Deployment was not successful. Error message from neo-node was: " +
                    response.getError().getMessage());
        }
        Hash256 txHash = response.getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);

        System.out.printf("\nDeployed contract with hash %s in transaction %s.\n", adapterContractHash, txHash);
    }

    // endregion deployment

}
