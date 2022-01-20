package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;

import static io.neow3j.devpack.Account.createStandardAccount;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;

@Permission(contract = "*", methods = "transfer")
@SuppressWarnings("unchecked")
public class GrantSharesTreasury {

    static final String OWNER_KEY = "owner";
    static final String FUNDERS_PREFIX = "funders";
    static final String FUNDER_COUNT_KEY = "funderCount";
    static final String WHITELISTED_TOKENS_PREFIX = "whitelistedTokens";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap funders = ctx.createMap(FUNDERS_PREFIX);
    static final StorageMap whitelistedTokens = ctx.createMap(WHITELISTED_TOKENS_PREFIX);

    @DisplayName("FunderAdded")
    static Event1Arg<Hash160> funderAdded;

    @DisplayName("FunderRemoved")
    static Event1Arg<Hash160> funderRemoved;

    @DisplayName("WhitelistedTokenAdded")
    static Event2Args<Hash160, Integer> whitelistedTokenAdded;

    @DisplayName("WhitelistedTokenRemoved")
    static Event1Arg<Hash160> whitelistedTokenRemoved;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] ownerFundersTokens = (Object[]) data;
            // Set owner
            Storage.put(ctx, OWNER_KEY, (Hash160) ownerFundersTokens[0]);

            // Set initial funders.
            ECPoint[] pubKeys = (ECPoint[]) ownerFundersTokens[1];
            for (ECPoint pubKey : pubKeys) {
                funders.put(createStandardAccount(pubKey).toByteString(), pubKey.toByteString());
            }
            Storage.put(ctx, FUNDER_COUNT_KEY, pubKeys.length);

            // Set whitelisted tokens
            Map<Hash160, Integer> tokens = (Map<Hash160, Integer>) ownerFundersTokens[2];
            Hash160[] hashes = tokens.keys();
            Integer[] maxes = tokens.values();
            for (int i = 0; i < hashes.length; i++) {
                whitelistedTokens.put(hashes[i].toByteString(), maxes[i]);
            }
        }
    }

    /**
     * Checks if the sender is an eligible funder and if the transferred token is whitelisted.
     * <p>
     * Fails if the contract is paused.
     *
     * @param sender The token sender.
     * @param amount The transferred amount.
     * @param data   Data sent with the transfer.
     */
    @Safe
    @OnNEP17Payment
    public static void onNep17Payment(Hash160 sender, int amount, Object data) {
        assertNotPaused();
        assert funders.get(sender.toByteString()) != null :
                "GrantSharesTreasury: Non-whitelisted sender";
        assert whitelistedTokens.get(Runtime.getCallingScriptHash().toByteString()) != null :
                "GrantSharesTreasury: Non-whitelisted token";
    }

    /**
     * Gets the number of funders eligible to send funds to the treasury.
     *
     * @return the number of funders.
     */
    @Safe
    public static int getFundersCount() {
        return Storage.getInt(ctx, FUNDER_COUNT_KEY);
    }

    /**
     * Gets all funders eligible to send funds to the treasury.
     *
     * @return the funders public keys.
     */
    @Safe
    public static List<ECPoint> getFunders() {
        Iterator<ByteString> it = Storage.find(ctx, FUNDERS_PREFIX, ValuesOnly);
        List<ECPoint> funders = new List<>();
        while (it.next()) {
            funders.add(new ECPoint(it.get()));
        }
        return funders;
    }

    /**
     * Gets all whitelisted tokens.
     *
     * @return a map of token hashes to the corresponding max funding amount set for the tokens.
     */
    @Safe
    public static Map<Hash160, Integer> getWhitelistedTokens() {
        Iterator<Struct<Hash160, Integer>> it = Storage.find(ctx, WHITELISTED_TOKENS_PREFIX,
                FindOptions.RemovePrefix);
        Map<Hash160, Integer> tokens = new Map<>();
        while (it.next()) {
            Struct<Hash160, Integer> i = it.get();
            tokens.put(i.key, i.value);
        }
        return tokens;
    }

    /**
     * Checks if the contract is paused.
     *
     * @return true if paused. False otherwise.
     */
    @Safe
    public static boolean isPaused() {
        return (boolean) Contract.call(
                new Hash160(Storage.get(ctx, OWNER_KEY)),
                "isPaused", CallFlags.ReadOnly, new Object[]{});
    }

    /**
     * Adds the given public key as a whitelisted funder to the treasury. Only whitelisted
     * funders can transfer tokens to the treasury.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param funderKey The public key of the funder.
     */
    public static void addFunder(ECPoint funderKey) {
        assertNotPaused();
        assertCallerIsOwner();
        Hash160 funderHash = createStandardAccount(funderKey);
        assert funders.get(funderHash.toByteString()) ==
                null : "GrantSharesTreasury: Already a funder";
        funders.put(funderHash.toByteString(), funderKey.toByteString());
        Storage.put(ctx, FUNDER_COUNT_KEY, Storage.getInt(ctx, FUNDER_COUNT_KEY) + 1);
        funderAdded.fire(funderHash);
    }

    /**
     * Removes the given public key from the whitelisted funders.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param funderKey The public key of the funder.
     */
    public static void removeFunder(ECPoint funderKey) {
        assertNotPaused();
        assertCallerIsOwner();
        Hash160 funderHash = createStandardAccount(funderKey);
        assert funders.get(funderHash.toByteString()) != null : "GrantSharesTreasury: Not a funder";
        funders.delete(funderHash.toByteString());
        Storage.put(ctx, FUNDER_COUNT_KEY, Storage.getInt(ctx, FUNDER_COUNT_KEY) - 1);
        funderRemoved.fire(funderHash);
    }

    /**
     * Adds the given token to the whitelist or overwrites the tokens max funding amount if it is
     * already whitelisted.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param token            The token to add to the whitelist.
     * @param maxFundingAmount The max funding amount for the token.
     */
    public static void addWhitelistedToken(Hash160 token, int maxFundingAmount) {
        assertNotPaused();
        assertCallerIsOwner();
        whitelistedTokens.put(token.toByteString(), maxFundingAmount);
        whitelistedTokenAdded.fire(token, maxFundingAmount);
    }

    /**
     * Removes the given token from the whitelist.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param token The token to remove from the whitelist.
     */
    public static void removeWhitelistedToken(Hash160 token) {
        assertNotPaused();
        assertCallerIsOwner();
        assert whitelistedTokens.get(token.toByteString()) != null :
                "GrantSharesTreasury: Non-whitelisted token";
        whitelistedTokens.delete(token.toByteString());
        whitelistedTokenRemoved.fire(token);
    }

    /**
     * Calls the transfer method of {@code tokenContract} with the given amount and receiver. The
     * sender being this treasury contract.
     * <p>
     * This method fails if the contract is paused.
     *
     * @param tokenContract The token to transfer.
     * @param to            The receiver of the transfer.
     * @param amount        The amount to transfer.
     */
    public static void releaseTokens(Hash160 tokenContract, Hash160 to, int amount) {
        assertNotPaused();
        assertCallerIsOwner();
        Object[] params = new Object[]{
                Runtime.getExecutingScriptHash(), to, amount, new Object[]{}};
        boolean result = (boolean) Contract.call(tokenContract, "transfer",
                (byte) (CallFlags.States | CallFlags.AllowNotify), params);
        assert result : "GrantSharesTreasury: Releasing tokens failed: " + StdLib.itoa(amount, 10)
                + " from " + tokenContract.toByteString().toString();
    }

    /**
     * Updates the contract to the new NEF and manifest.
     * <p>
     * This method can only be called by the owner. It can be called even if the contract is paused
     * in case the contract needs a fix.
     */
    public static void update(ByteString nef, String manifest, Object data) {
        assertCallerIsOwner();
        ContractManagement.update(nef, manifest, data);
    }

    private static void assertCallerIsOwner() {
        assert Runtime.getCallingScriptHash().toByteString() ==
                Storage.get(ctx, OWNER_KEY) : "GrantSharesTreasury: Not authorised";
    }

    private static void assertNotPaused() {
        assert !isPaused() : "GrantSharesTreasury: Contract is paused";
    }

}