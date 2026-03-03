package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
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
import io.neow3j.devpack.events.Event1Arg;

import static io.neow3j.devpack.Helper.abort;
import static io.neow3j.devpack.Runtime.getCallingScriptHash;
import static io.neow3j.devpack.Runtime.getExecutingScriptHash;

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

    private final static int VERSION_KEY = 0x00;
    private final static int OWNER_KEY = 0x01;
    private final static int GRANTSHARESGOV_CONTRACT_KEY = 0x02;
    private final static int GRANTSHARESTREASURY_CONTRACT_KEY = 0x03;
    private final static int BRIDGE_CONTRACT_KEY = 0x04;
    private static final int MAX_FEE_KEY = 0x05;
    private static final int WHITELISTED_FUNDER_KEY = 0x06;

    private static final int V1_BRIDGE_VERSION_KEY = 0x10;

    //region events

    @DisplayName("WhitelistedFunderAdded")
    static Event1Arg<Hash160> whitelistedFunderAdded;
    @DisplayName("MaxFeeChanged")
    static Event1Arg<Integer> maxFeeChanged;

    // endregion events
    // region authorization

    private static void checkOwner() {
        if (!Runtime.checkWitness(owner())) {
            abort("unauthorized");
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

    /**
     * Deposits the amount of the token to a beneficiary on Neo X. This contract must hold exactly the specified
     * amount of tokens when this function is called. If the contract holds any tokens after the bridge deposit, the
     * transaction will be aborted.
     * <p>
     * The contract's GAS balance is an exception to this rule since the deposit fee might differ based on the token
     * or might change during a proposal's lifetime. Therefore, this contrac's {@code maxFee} value is used as
     * upperbound of how many GAS tokens may still be held by this contract after the bridge deposit. The {@code
     * maxFee} value can be fetched with {@link GrantSharesBridgeAdapter#maxFee()}.
     * <p>
     * The bridge fee will be paid by the treasury using a separate intent.
     *
     * @param token  the token to bridge.
     * @param to     the recipient of the bridged tokens.
     * @param amount the amount of tokens to bridge.
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
        int gasBalanceSelf = gasToken.balanceOf(executingScriptHash);
        int maxFee = maxFee();

        if (token.equals(gasToken.getHash())) {
            if (gasBalanceSelf < amount + bridgeContract.nativeDepositFee()) {
                abort("insufficient gas balance for bridge deposit");
            }
            bridgeContract.depositNative(executingScriptHash, to, amount, maxFee);
        } else if (token.equals(new NeoToken().getHash())) {
            if (gasBalanceSelf < bridgeContract.tokenDepositFee(token)) {
                abort("insufficient gas balance for bridge fee");
            }
            if (new FungibleToken(token).balanceOf(executingScriptHash) < amount) {
                abort("insufficient token balance for bridge deposit");
            }
            bridgeContract.depositToken(token, executingScriptHash, to, amount, maxFee);
            // Checks that this contract holds no tokens after the deposit. Aborts if it still holds any tokens.
            checkTokenBalance(new FungibleToken(token), executingScriptHash, 0);
        } else {
            abort("unsupported token");
        }
        // Checks that this contract holds maximally the maxFee after the deposit. Aborts if it holds more GAS.
        checkTokenBalance(gasToken, executingScriptHash, maxFee);
    }

    private static void checkTokenBalance(FungibleToken token, Hash160 account, int maxAllowed) {
        if (token.balanceOf(account) > maxAllowed) {
            abort("unallowed token balance remainder");
        }
    }

    // endregion bridge function
    // region NEP17 payment

    /**
     * This contract accepts the following NEP-17 payments:
     * <ul>
     *     <li>GAS and NEO from the GrantShares treasury</li>
     *     <li>GAS from the whitelisted funder</li>
     *     <li>Minted GAS in the unplanned event of this contract holding NEO</li>
     * </ul>
     * <p>
     * Provided data is ignored.
     *
     * @param from   the sender of tokens.
     * @param amount the amount sent.
     * @param data   abritrary data.
     */
    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 from, int amount, Object data) {
        Hash160 callingScriptHash = Runtime.getCallingScriptHash();

        if (callingScriptHash.equals(new GasToken().getHash())) {
            if (from == null) { // Allow Gas minting from potentially holding Neo.
                return;
            } else if (from.equals(grantSharesTreasuryContract()) || from.equals(whitelistedFunder())) {
                return;
            } else {
                abort("only treasury or whitelisted funder");
            }
        } else if (callingScriptHash.equals(new NeoToken().getHash())) {
            if (from.equals(grantSharesTreasuryContract())) {
                return;
            } else {
                abort("only treasury");
            }
        } else {
            abort("unsupported token");
        }
    }

    // endregion
    // region setters

    /**
     * Sets the whitelisted funder that is allowed to send GAS to this contract.
     *
     * @param funder the new whitelisted funder.
     */
    public static void setWhitelistedFunder(Hash160 funder) {
        checkOwner();
        if (funder == null || !Hash160.isValid(funder) || funder.isZero()) {
            abort("invalid funder");
        }
        Storage.put(WHITELISTED_FUNDER_KEY, funder);
        whitelistedFunderAdded.fire(funder);
    }

    /**
     * Sets the {@code maxFee} that is used as parameter to the bridge's deposit interface.
     *
     * @param maxFee the max fee used for bridge deposits.
     */
    public static void setMaxFee(Integer maxFee) {
        checkOwner();
        if (maxFee == null || maxFee < 0) {
            abort("invalid max fee");
        }
        Storage.put(MAX_FEE_KEY, maxFee);
        maxFeeChanged.fire(maxFee);
    }

    // endregion setters
    // region read-only methods

    @Safe
    public static Hash160 owner() {
        return Storage.getHash160(OWNER_KEY);
    }

    /**
     * @return the max fee that is used for all the bridge's token deposit functions.
     */
    @Safe
    public static int maxFee() {
        return Storage.getInt(MAX_FEE_KEY);
    }

    /**
     * @return the funder that is allowed to send GAS to this contract.
     */
    @Safe
    public static Hash160 whitelistedFunder() {
        return Storage.getHash160(WHITELISTED_FUNDER_KEY);
    }

    @Safe
    public static Hash160 grantSharesGovContract() {
        return Storage.getHash160(GRANTSHARESGOV_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 grantSharesTreasuryContract() {
        return Storage.getHash160(GRANTSHARESTREASURY_CONTRACT_KEY);
    }

    @Safe
    public static Hash160 bridgeContract() {
        return Storage.getHash160(BRIDGE_CONTRACT_KEY);
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
        if (update) {
            if (Storage.getInt(VERSION_KEY) != 1) {
                abort("invalid version");
            }
            Storage.put(VERSION_KEY, 2);
            Storage.delete(V1_BRIDGE_VERSION_KEY);
        } else {
            Storage.put(VERSION_KEY, 1);

            // Initialize the contract.
            DeployData deployData = (DeployData) data;
            Hash160 initialOwner = deployData.initialOwner;
            if (initialOwner == null || !Hash160.isValid(initialOwner) || initialOwner.isZero()) {
                abort("invalid initial owner");
            }
            Storage.put(OWNER_KEY, initialOwner);

            Hash160 gsGovContract = deployData.grantSharesGovContract;
            if (gsGovContract == null || !Hash160.isValid(gsGovContract) || gsGovContract.isZero()) {
                abort("invalid GrantSharesGov contract");
            }
            Storage.put(GRANTSHARESGOV_CONTRACT_KEY, gsGovContract);

            Hash160 gsTreasury = deployData.grantSharesTreasuryContract;
            if (gsTreasury == null || !Hash160.isValid(gsTreasury) || gsTreasury.isZero()) {
                abort("invalid GrantSharesTreasury contract");
            }
            Storage.put(GRANTSHARESTREASURY_CONTRACT_KEY, gsTreasury);

            Hash160 bridgeContract = deployData.bridgeContract;
            if (bridgeContract == null || !Hash160.isValid(bridgeContract) || bridgeContract.isZero()) {
                abort("invalid bridge contract");
            }
            Storage.put(BRIDGE_CONTRACT_KEY, bridgeContract);

            Integer initialMaxFee = deployData.initialMaxFee;
            if (initialMaxFee == null || initialMaxFee < 0) {
                abort("invalid initial max fee");
            }
            Storage.put(MAX_FEE_KEY, initialMaxFee);

            Hash160 whitelistedFunder = deployData.initialWhitelistedFunder;
            if (whitelistedFunder == null || !Hash160.isValid(whitelistedFunder) || whitelistedFunder.isZero()) {
                abort("invalid whitelisted funder");
            }
            Storage.put(WHITELISTED_FUNDER_KEY, whitelistedFunder);
        }
    }

    public static void update(ByteString nef, String manifest, Object data) {
        checkOwner();
        new ContractManagement().update(nef, manifest, data);
    }

    // endregion deployment/update
    // region bridge interface

    /**
     * The bridge contract interface.
     * <p>
     * The interface only includes the deposit and fee functions for version 3 of the bridge.
     * <p>
     * If there is a new bridge version in the future with changes to the below interface, this contract will need to
     * be updated.
     */
    static class BridgeContract extends ContractInterface {
        BridgeContract(Hash160 contractHash) {
            super(contractHash);
        }

        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositNative(Hash160 from, Hash160 to, int amount, int maxFee);

        @CallFlags(io.neow3j.devpack.constants.CallFlags.All)
        native void depositToken(Hash160 neoN3Token, Hash160 from, Hash160 to, int amount, int maxFee);

        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates | io.neow3j.devpack.constants.CallFlags.AllowCall)
        native int nativeDepositFee();

        @CallFlags(io.neow3j.devpack.constants.CallFlags.ReadStates | io.neow3j.devpack.constants.CallFlags.AllowCall)
        native int tokenDepositFee(Hash160 token);
    }

    // endregion

}
