package com.axlabs.neo.grantshares;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.serialization.exceptions.DeserializationException;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

import static io.neow3j.contract.ContractUtils.writeContractManifestFile;
import static io.neow3j.contract.ContractUtils.writeNefFile;
import static io.neow3j.transaction.AccountSigner.none;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;

public class DeployAdapter {

    private static Account deployer = Account.fromWIF("");
    private static Neow3j neow3j = Neow3j.build(new HttpService(""));
    private static final String CONTRACT_NAME = "GrantSharesBridgeAdapter";

    public static void main(String[] args) throws Throwable {
        compileGrantSharesBridgeAdapter();
        deployGrantSharesBridgeAdapter();
    }

    // region deployment

    private static void deployGrantSharesBridgeAdapter() throws Throwable {
        NefFile nefFile = readGrantSharesBridgeAdapterNefFile();
        ContractManifest manifest = readGrantSharesBridgeAdapterManifest();

        Hash160 adapterContractHash = SmartContract.calcContractHash(
                deployer.getScriptHash(),
                nefFile.getCheckSumAsInteger(),
                manifest.getName()
        );

        Hash160 initialOwner = deployer.getScriptHash();
        // The following hashes are the contract hashes on testnet.
        Hash160 govContractHash = new Hash160("0x7f96d67752ac47928742bc518f528d0f42964645");
        Hash160 treasuryContractHash = new Hash160("0xc8dc114f3986579b8f318a484ff0e5f97d120fab");
        Hash160 bridgeContractHash = new Hash160("0x2ba94444d43c9a084a5660982a9f95f43f07422e");
        BigInteger initialMaxFee = new GasToken(neow3j).toFractions(new BigDecimal("0.1"));
        Hash160 initialWhitelistedFunder = deployer.getScriptHash();

        ContractParameter deploymentParam = array(
                hash160(initialOwner),
                hash160(govContractHash),
                hash160(treasuryContractHash),
                hash160(bridgeContractHash),
                integer(initialMaxFee),
                hash160(initialWhitelistedFunder)

        );

        TransactionBuilder b = new ContractManagement(neow3j).deploy(nefFile, manifest, deploymentParam)
                .signers(none(deployer).setAllowedContracts(adapterContractHash));
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
    // region read compilation output

    private static NefFile readGrantSharesBridgeAdapterNefFile() throws IOException, DeserializationException {
        File contractNefFile = Paths.get("build", "neow3j", CONTRACT_NAME + ".nef").toFile();
        return NefFile.readFromFile(contractNefFile);
    }

    private static ContractManifest readGrantSharesBridgeAdapterManifest() throws IOException {
        File contractManifestFile = Paths.get("build", "neow3j", CONTRACT_NAME + ".manifest.json").toFile();
        ContractManifest manifest;
        try (FileInputStream s = new FileInputStream(contractManifestFile)) {
            manifest = ObjectMapperFactory.getObjectMapper().readValue(s, ContractManifest.class);
        }
        return manifest;
    }

    // endregion read compilation output
    // region compilation

    private static void compileGrantSharesBridgeAdapter() throws IOException {
        CompilationUnit compUnit = compile(GrantSharesBridgeAdapter.class);
        writeNefAndManifestFiles(compUnit);
    }

    /**
     * Compiles the contractClass and writes the resulting Nef and manifest to files.
     */
    public static CompilationUnit compile(Class<?> contractClass) throws IOException {
        CompilationUnit compUnit = new Compiler().compile(contractClass.getCanonicalName());
        writeNefAndManifestFiles(compUnit);
        return compUnit;
    }

    public static void writeNefAndManifestFiles(CompilationUnit compUnit) throws IOException {
        // Write contract (compiled, NEF) to the disk
        Path buildNeow3jPath = Paths.get("build", "neow3j");
        buildNeow3jPath.toFile().mkdirs();
        writeNefFile(compUnit.getNefFile(), compUnit.getManifest().getName(), buildNeow3jPath);

        // Write manifest to the disk
        writeContractManifestFile(compUnit.getManifest(), buildNeow3jPath);
    }

    // endregion compilation

}
