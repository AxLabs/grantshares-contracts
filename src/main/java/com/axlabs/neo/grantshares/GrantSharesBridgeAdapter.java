package com.axlabs.neo.grantshares;

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
import io.neow3j.devpack.contracts.ContractInterface;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;

import static io.neow3j.devpack.Helper.abort;
import static io.neow3j.devpack.Runtime.getExecutingScriptHash;
import static io.neow3j.devpack.Storage.getStorageContext;

@Permission(contract = "*", methods = "*")
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

    static StorageContext context = getStorageContext();

    private final static int VERSION_KEY = 0x00;
    private final static int OWNER_KEY = 0x01;
    private final static int GRANTSHARESGOV_CONTRACT_KEY = 0x02;
    private final static int GRANTSHARESTREASURY_CONTRACT_KEY = 0x03;
    private final static int BRIDGE_CONTRACT_KEY = 0x04;
    private static final int MAX_FEE_KEY = 0x05;
    private static final int BACKEND_ACCOUNT_KEY = 0x06;
    private static final int WHITELISTED_FUNDER_KEY = 0x07;

    @Struct
    static class DeployParams {
        Hash160 initialOwner;
        Hash160 grantSharesGovContract;
        Hash160 grantSharesTreasuryContract;
        Hash160 bridgeContract;
        Integer initialMaxFee;
        Hash160 initialBackendAccount;
        Hash160 initialWhitelistedFunder;
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

            Hash160 gsTreasury = deployParams.grantSharesTreasuryContract;
            if (gsGovContract == null || !Hash160.isValid(gsTreasury) || gsTreasury.isZero()) {
                abort("invalid GrantSharesTreasury contract");
            }
            Storage.put(context, GRANTSHARESTREASURY_CONTRACT_KEY, gsTreasury);

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

            Hash160 backendAccount = deployParams.initialBackendAccount;
            if (backendAccount == null || !Hash160.isValid(backendAccount) || backendAccount.isZero()) {
                abort("invalid backend account");
            }
            Storage.put(context, BACKEND_ACCOUNT_KEY, backendAccount);

            Hash160 whitelistedFunder = deployParams.initialWhitelistedFunder;
            if (whitelistedFunder == null || !Hash160.isValid(whitelistedFunder) || whitelistedFunder.isZero()) {
                abort("invalid whitelisted funder");
            }
            Storage.put(context, WHITELISTED_FUNDER_KEY, whitelistedFunder);
        }
    }

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

    @OnVerification
    public static boolean verify() {
        return Runtime.checkWitness(backendAccount());
    }

    @Safe
    public static Hash160 backendAccount() {
        return Storage.getHash160(context.asReadOnly(), BACKEND_ACCOUNT_KEY);
    }

    public static void setBackendAccount(Hash160 account) {
        onlyOwner();
        if (account == null || !Hash160.isValid(account) || account.isZero()) {
            abort("invalid account");
        }
        Storage.put(context, BACKEND_ACCOUNT_KEY, account);
    }

    @Safe
    public static Hash160 whitelistedFunder() {
        return Storage.getHash160(context.asReadOnly(), WHITELISTED_FUNDER_KEY);
    }

    public static void setWhitelistedFunder(Hash160 funder) {
        onlyOwner();
        if (funder == null || !Hash160.isValid(funder) || funder.isZero()) {
            abort("invalid funder");
        }
        Storage.put(context, WHITELISTED_FUNDER_KEY, funder);
    }

    /**
     * The bridge fee will be paid by the treasury using a separate intent.
     *
     * @param token the token to bridge.
     * @param to    the recipient of the bridged tokens.
     */
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

        Hash160 executingScriptHash = getExecutingScriptHash();
        BridgeContract bridgeContract = new BridgeContract(bridgeContract());
        int maxFee = maxFee();

        int bridgeFee = bridgeContract.getFee();
        FungibleToken tokenContract = new FungibleToken(token);

        int balance = tokenContract.balanceOf(executingScriptHash);
        if (balance < amount) abort("insufficient balance");

        if (token.equals(new GasToken().getHash())) {
            if (balance < amount + bridgeFee) abort("insufficient balance for fee");
            bridgeContract.depositNative(executingScriptHash, to, amount, maxFee);
        } else if (token.equals(new NeoToken().getHash())) {
            bridgeContract.depositToken(token, executingScriptHash, to, amount, maxFee);
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
    public static Hash160 grantSharesTrasuryContract() {
        return Storage.getHash160(context.asReadOnly(), GRANTSHARESTREASURY_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 bridgeContract() {
        return Storage.getHash160(context.asReadOnly(), BRIDGE_CONTRACT_KEY);
    }

    static class BridgeContract extends ContractInterface {

        BridgeContract(Hash160 contractHash) {
            super(contractHash);
        }

        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositNative(Hash160 from, Hash160 to, int amount, int maxFee);

        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositToken(Hash160 neoN3Token, Hash160 from, Hash160 to, int amount, int maxFee);

        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates)
        native int getFee();
    }

}
