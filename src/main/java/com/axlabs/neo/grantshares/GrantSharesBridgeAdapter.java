package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.annotations.CallFlags;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.OnVerification;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.Struct;
import io.neow3j.devpack.constants.NativeContract;
import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;

import static io.neow3j.devpack.Helper.abort;
import static io.neow3j.devpack.Runtime.getCallingScriptHash;
import static io.neow3j.devpack.Runtime.getExecutingScriptHash;
import static io.neow3j.devpack.Storage.getStorageContext;

@Permission.Permissions({
        @Permission(contract = "*", methods = {"depositGas", "depositNative", "depositToken"}),
        @Permission(nativeContract = NativeContract.ContractManagement, methods = "update")
})
@ManifestExtra(key = "Author", value = "AxLabs")
@ManifestExtra(key = "Email", value = "info@grantshares.io")
@ManifestExtra(key = "Description", value = "The GrantShares bridge adapter contract.")
@ManifestExtra(key = "Website", value = "https://grantshares.io")
//@formatter:off
@ContractSourceCode("https://github.com/AxLabs/grantshares-contracts/blob/main/src/main/java/com/axlabs/neo/grantshares/GrantSharesBridgeAdapter.java")
//@formatter:on
@DisplayName("GrantSharesBridgeAdapter")
@SuppressWarnings("unchecked")
public class GrantSharesBridgeAdapter {

    private static StorageContext context = getStorageContext();

    private final static int VERSION_KEY = 0x00;
    private final static int OWNER_KEY = 0x01;
    private final static int GRANTSHARESGOV_CONTRACT_KEY = 0x02;
    private final static int GRANTSHARESTREASURY_CONTRACT_KEY = 0x03;
    private final static int BRIDGE_CONTRACT_KEY = 0x04;
    private static final int MAX_FEE_KEY = 0x05;
    private static final int WHITELISTED_FUNDER_KEY = 0x06;

    private static final int BRIDGE_VERSION_KEY = 0x10;

    // region authorization

    private static void onlyOwner() {
        if (!Runtime.checkWitness(owner())) {
            abort("only owner");
        }
    }

    // endregion authorization
    // region verify

    @OnVerification
    public static boolean verify() {
        // This contract is not intended to hold any tokens in between two transactions. In each transaction where
        // tokens are received, they are intended to be forwarded immediately.
        return true;
    }

    // endregion verify
    // region bridge function

    @Safe
    public static int bridgeVersion() {
        return Storage.getInt(context, BRIDGE_VERSION_KEY);
    }

    public static void setBridgeVersion(Integer version) {
        onlyOwner();
        if (version == null || version < 2 || version > 3) {
            abort("unsupported bridge version");
        }
        Storage.put(context, BRIDGE_VERSION_KEY, version);
    }

    /**
     * The bridge fee will be paid by the treasury using a separate intent.
     *
     * @param token the token to bridge.
     * @param to    the recipient of the bridged tokens.
     */
    public static void bridge(Hash160 token, Hash160 to, Integer amount) {
        if (!getCallingScriptHash().equals(grantSharesGovContract())) {
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

        Hash160 executingScriptHash = getExecutingScriptHash();
        BridgeContract bridgeContract = new BridgeContract(bridgeContract());
        GasToken gasToken = new GasToken();

        if (token.equals(gasToken.getHash())) {
            if (bridgeVersion() == 2) {
                int amountIncludingFee = amount + bridgeContract.gasDepositFee();
                if (gasToken.balanceOf(executingScriptHash) < amountIncludingFee) {
                    abort("insufficient gas balance for bridge deposit");
                }
                bridgeContract.depositGas(executingScriptHash, to, amountIncludingFee, maxFee());
                return;
            } else if (bridgeVersion() == 3) {
                if (gasToken.balanceOf(executingScriptHash) < amount + bridgeContract.nativeDepositFee()) {
                    abort("insufficient gas balance for bridge deposit");
                }
                bridgeContract.depositNative(executingScriptHash, to, amount, maxFee());
                return;
            }
        } else if (token.equals(new NeoToken().getHash())) {
            if (gasToken.balanceOf(executingScriptHash) < bridgeContract.tokenDepositFee(token)) {
                abort("insufficient gas balance for bridge fee");
            }
            if (new FungibleToken(token).balanceOf(executingScriptHash) < amount) {
                abort("insufficient token balance for bridge deposit");
            }
            bridgeContract.depositToken(token, executingScriptHash, to, amount, maxFee());
        } else {
            abort("unsupported token");
        }
    }

    // endregion bridge function
    // region NEP17 payment

    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 from, int amount, Object data) {
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();
        boolean isGas = callingScriptHash.equals(new GasToken().getHash());

        if (from == null) {
            // Only Gas-minting is allowed.
            if (!isGas) {
                abort("minting only allowed for gas");
            } else {
                // Gas minting is accepted.
                return;
            }
        } else {
            if (from.equals(whitelistedFunder()) && isGas) {
                // Whitelisted funder is allowed to send Gas tokens.
                return;
            }
            if (!from.equals(grantSharesTrasuryContract())) {
                abort("only treasury");
            } else {
                // Accept only Gas and Neo tokens.
                if (isGas) {
                    return;
                } else if (callingScriptHash.equals(new NeoToken().getHash())) {
                    return;
                } else {
                    abort("unsupported token");
                }
            }
        }
    }

    // endregion
    // region setters

    public static void setWhitelistedFunder(Hash160 funder) {
        onlyOwner();
        if (funder == null || !Hash160.isValid(funder) || funder.isZero()) {
            abort("invalid funder");
        }
        Storage.put(context, WHITELISTED_FUNDER_KEY, funder);
    }

    public static void setMaxFee(Integer maxFee) {
        onlyOwner();
        if (maxFee == null || maxFee < 0) {
            abort("invalid max fee");
        }
        Storage.put(context, MAX_FEE_KEY, maxFee);
    }

    // endregion setters
    // region read-only methods

    @Safe
    public static Hash160 owner() {
        return Storage.getHash160(context.asReadOnly(), OWNER_KEY);
    }

    @Safe
    public static int maxFee() {
        return Storage.getInt(context.asReadOnly(), MAX_FEE_KEY);
    }

    @Safe
    public static Hash160 whitelistedFunder() {
        return Storage.getHash160(context.asReadOnly(), WHITELISTED_FUNDER_KEY);
    }

    @Safe
    public static Hash160 grantSharesGovContract() {
        return Storage.getHash160(context.asReadOnly(), GRANTSHARESGOV_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 grantSharesTrasuryContract() {
        return Storage.getHash160(context.asReadOnly(), GRANTSHARESTREASURY_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 bridgeContract() {
        return Storage.getHash160(context.asReadOnly(), BRIDGE_CONTRACT_KEY);
    }

    // endregion
    // region deployment/update

    @Struct
    static class DeployData {
        Hash160 initialOwner;
        Hash160 grantSharesGovContract;
        Hash160 grantSharesTreasuryContract;
        Hash160 bridgeContract;
        Integer initialMaxFee;
        Hash160 initialWhitelistedFunder;
    }

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(context, VERSION_KEY, 1);

            // Initialize the contract.
            DeployData deployData = (DeployData) data;
            Hash160 initialOwner = deployData.initialOwner;
            if (initialOwner == null || !Hash160.isValid(initialOwner) || initialOwner.isZero()) {
                abort("invalid initial owner");
            }
            Storage.put(context, OWNER_KEY, initialOwner);
            onlyOwner();

            Hash160 gsGovContract = deployData.grantSharesGovContract;
            if (gsGovContract == null || !Hash160.isValid(gsGovContract) || gsGovContract.isZero()) {
                abort("invalid GrantSharesGov contract");
            }
            Storage.put(context, GRANTSHARESGOV_CONTRACT_KEY, gsGovContract);

            Hash160 gsTreasury = deployData.grantSharesTreasuryContract;
            if (gsGovContract == null || !Hash160.isValid(gsTreasury) || gsTreasury.isZero()) {
                abort("invalid GrantSharesTreasury contract");
            }
            Storage.put(context, GRANTSHARESTREASURY_CONTRACT_KEY, gsTreasury);

            Hash160 bridgeContract = deployData.bridgeContract;
            if (bridgeContract == null || !Hash160.isValid(bridgeContract) || bridgeContract.isZero()) {
                abort("invalid bridge contract");
            }
            Storage.put(context, BRIDGE_CONTRACT_KEY, bridgeContract);

            Integer initialMaxFee = deployData.initialMaxFee;
            if (initialMaxFee == null || initialMaxFee < 0) {
                abort("invalid initial max fee");
            }
            Storage.put(context, MAX_FEE_KEY, initialMaxFee);

            Hash160 whitelistedFunder = deployData.initialWhitelistedFunder;
            if (whitelistedFunder == null || !Hash160.isValid(whitelistedFunder) || whitelistedFunder.isZero()) {
                abort("invalid whitelisted funder");
            }
            Storage.put(context, WHITELISTED_FUNDER_KEY, whitelistedFunder);

            Storage.put(context, BRIDGE_VERSION_KEY, 2);
        }
    }

    public static void update(ByteString nef, String manifest, Object data) {
        onlyOwner();
        new ContractManagement().update(nef, manifest, data);
    }

    // endregion deployment/update
    // region bridge interface

    static class BridgeContract extends ContractInterface {
        BridgeContract(Hash160 contractHash) {
            super(contractHash);
        }

        // v2
        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositGas(Hash160 from, Hash160 to, int amount, int maxFee);

        // v3
        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositNative(Hash160 from, Hash160 to, int amount, int maxFee);

        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositToken(Hash160 neoN3Token, Hash160 from, Hash160 to, int amount, int maxFee);

        // v2
        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates)
        native int gasDepositFee();

        // v3
        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates)
        native int nativeDepositFee();

        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates)
        native int tokenDepositFee(Hash160 token);
    }

    // endregion

}
