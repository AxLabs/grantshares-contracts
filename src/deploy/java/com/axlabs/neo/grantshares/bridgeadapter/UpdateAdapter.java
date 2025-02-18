package com.axlabs.neo.grantshares.bridgeadapter;

import com.axlabs.neo.grantshares.GrantSharesBridgeAdapter;
import io.neow3j.constants.NeoConstants;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoSendRawTransaction;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import static com.axlabs.neo.grantshares.bridgeadapter.CompilationHelper.compileAndWriteNefAndManifestFiles;
import static com.axlabs.neo.grantshares.bridgeadapter.CompilationHelper.readManifest;
import static com.axlabs.neo.grantshares.bridgeadapter.CompilationHelper.readNefFile;
import static com.axlabs.neo.grantshares.bridgeadapter.AccountHelper.getBridgeAdapterOwner;
import static io.neow3j.transaction.AccountSigner.calledByEntry;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.byteArray;
import static java.lang.String.format;

public class UpdateAdapter {

    // N3 testnet node
    private static final Neow3j neow3j = Neow3j.build(new HttpService("http://seed1t5.neo.org:20332"));

    private static final Hash160 bridgeAdapter = new Hash160("0x8346705e69ecc085f2216b4836659686dbab59ee");

    private static final String BRIDGE_ADAPTER_CONTRACT_NAME = "GrantSharesBridgeAdapter";

    public static void main(String[] args) throws Throwable {
        Account bridgeAdapterOwner = getBridgeAdapterOwner();

        compileAndWriteNefAndManifestFiles(GrantSharesBridgeAdapter.class);
        updateGrantSharesBridgeAdapter(bridgeAdapterOwner);
    }

    private static void updateGrantSharesBridgeAdapter(Account bridgeAdapterOwner) throws Throwable {
        NefFile nefFile = readNefFile(BRIDGE_ADAPTER_CONTRACT_NAME);
        ContractManifest manifest = readManifest(BRIDGE_ADAPTER_CONTRACT_NAME);
        if (nefFile == null) {
            throw new IllegalArgumentException("The NEF file cannot be null.");
        }
        if (manifest == null) {
            throw new IllegalArgumentException("The manifest cannot be null.");
        }
        byte[] manifestBytes = ObjectMapperFactory.getObjectMapper().writeValueAsBytes(manifest);
        if (manifestBytes.length > NeoConstants.MAX_MANIFEST_SIZE) {
            throw new IllegalArgumentException(format("The given contract manifest is too long. Manifest was %d bytes" +
                    " big, but a max of %d bytes is allowed.", manifestBytes.length, NeoConstants.MAX_MANIFEST_SIZE
            ));
        }

        ContractParameter dataParam = any(null);

        TransactionBuilder b = new SmartContract(bridgeAdapter, neow3j)
                .invokeFunction("update", byteArray(nefFile.toArray()), byteArray(manifestBytes), dataParam)
                .signers(calledByEntry(bridgeAdapterOwner));
        NeoSendRawTransaction response = b.sign().send();

        if (response.hasError()) {
            throw new Exception(
                    "Update was not successful. Error message from neo-node was: " + response.getError().getMessage());
        }
        Hash256 txHash = response.getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);

        System.out.printf("\nUpdated contract with hash %s in transaction %s.\n", bridgeAdapter, txHash);
    }
}
