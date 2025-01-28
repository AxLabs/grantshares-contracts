package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.Struct;
import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;

import static io.neow3j.devpack.Helper.abort;
import static io.neow3j.devpack.Runtime.getExecutingScriptHash;
import static io.neow3j.devpack.Storage.getStorageContext;

@Permission(contract = "*", methods = "*")
@ManifestExtra(key = "Author", value = "AxLabs")
@ManifestExtra(key = "Email", value = "info@grantshares.io")
@ManifestExtra(key = "Description", value = "The GrantShares bridge adaptor contract.")
@ManifestExtra(key = "Website", value = "https://grantshares.io")
//@formatter:off
@ContractSourceCode("https://github.com/AxLabs/grantshares-contracts/blob/main/src/main/java/com/axlabs/neo/grantshares/GrantSharesBridgeAdaptor.java")
//@formatter:on
@DisplayName("GrantSharesBridgeAdaptor")
@SuppressWarnings("unchecked")
public class GrantSharesBridgeAdaptor {

    static StorageContext context = getStorageContext();

    private final static int VERSION_KEY = 0x00;
    private final static int OWNER_KEY = 0x01;
    private final static int GRANTSHARESGOV_CONTRACT_KEY = 0x02;
    private final static int BRIDGE_CONTRACT_KEY = 0x03;
    private static final int MAX_FEE_KEY = 0x04;

    @Struct
    static class DeployParams {
        Hash160 initialOwner;
        Hash160 grantSharesGovContract;
        Hash160 bridgeContract;
        Integer initialMaxFee;
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(context, VERSION_KEY, 1);

            // Initialize the contract.
            DeployParams deployParams = (DeployParams) data;
            Hash160 initialOwner = deployParams.initialOwner;
            if (initialOwner == null || !Hash160.isValid(initialOwner) || initialOwner.isZero()) {
                abort("invalid initial owner");
            }
            Storage.put(context, OWNER_KEY, initialOwner);
            onlyOwner();

            Hash160 gsGovContract = deployParams.grantSharesGovContract;
            if (gsGovContract == null || !Hash160.isValid(gsGovContract) || gsGovContract.isZero()) {
                abort("invalid GrantSharesGov contract");
            }
            Storage.put(context, GRANTSHARESGOV_CONTRACT_KEY, gsGovContract);

            Hash160 bridgeContract = deployParams.bridgeContract;
            if (bridgeContract == null || !Hash160.isValid(bridgeContract) || bridgeContract.isZero()) {
                abort("invalid bridge contract");
            }
            Storage.put(context, BRIDGE_CONTRACT_KEY, bridgeContract);

            Integer initialMaxFee = deployParams.initialMaxFee;
            if (initialMaxFee == null || initialMaxFee < 0) {
                abort("invalid initial max fee");
            }
            Storage.put(context, MAX_FEE_KEY, initialMaxFee);
        }
    }

    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 from, int amount, Object data) {
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        if (callingScriptHash.equals(new GasToken().getHash())) {
            return;
        } else if (callingScriptHash.equals(new NeoToken().getHash())) {
            return;
        } else {
            abort("unsupported token");
        }
    }

    // Todo: the sender also needs to pay the bridge fee, in this case this adaptor contract. That means, the adaptor contract should have a gas balance to pay that fee.
    //  Either we add another intent to transfer the bridge fee in gas to this adaptor from the treasury, or we provide some gas funds to this adaptor contract and pay for it.
    public static void bridge(Hash160 token, Hash160 to, Integer amount) {
        if (!Runtime.getCallingScriptHash().equals(grantSharesGovContract())) {
            abort("only GrantSharesGov contract");
        }
        if (token == null || !Hash160.isValid(token) || token.isZero()) {
            abort("invalid token");
        }
        if (to == null || !Hash160.isValid(to) || to.isZero()) {
            abort("invalid to");
        }
        if (amount == null || amount <= 0) {
            abort("invalid amount");
        }

        BridgeContract bridgeContract = new BridgeContract(bridgeContract());
        if (token.equals(new GasToken().getHash())) {
            bridgeContract.depositNative(getExecutingScriptHash(), to, amount, maxFee());
        } else if (token.equals(new NeoToken().getHash())) {
            bridgeContract.depositToken(token, getExecutingScriptHash(), to, amount, maxFee());
        } else {
            abort("unsupported token");
        }
    }

    private static void onlyOwner() {
        if (!Runtime.checkWitness(owner())) {
            abort("only owner");
        }
    }

    public static void setMaxFee(int maxFee) {
        onlyOwner();
        if (maxFee < 0) {
            abort("invalid max fee");
        }
        Storage.put(context, MAX_FEE_KEY, maxFee);
    }

    @Safe
    public static int maxFee() {
        return Storage.getInt(context.asReadOnly(), MAX_FEE_KEY);
    }

    @Safe
    public static Hash160 owner() {
        return Storage.getHash160(context.asReadOnly(), OWNER_KEY);
    }

    @Safe
    public static Hash160 grantSharesGovContract() {
        return Storage.getHash160(context.asReadOnly(), GRANTSHARESGOV_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 bridgeContract() {
        return Storage.getHash160(context.asReadOnly(), BRIDGE_CONTRACT_KEY);
    }

    static class BridgeContract extends ContractInterface {

        BridgeContract(Hash160 contractHash) {
            super(contractHash);
        }

        native boolean depositNative(Hash160 from, Hash160 to, int amount, int maxFee);

        native boolean depositToken(Hash160 neoN3Token, Hash160 from, Hash160 to, int amount, int maxFee);
    }

}
