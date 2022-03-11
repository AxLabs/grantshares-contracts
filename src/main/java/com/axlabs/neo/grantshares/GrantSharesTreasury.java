package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Account;
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
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;

import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.constants.FindOptions.KeysOnly;
import static io.neow3j.devpack.constants.FindOptions.RemovePrefix;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;

@SuppressWarnings("unchecked")
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "update") // ContractManagement
@Permission(contract = "0xef4073a0f2b305a38ec4050e4d3d28bc40ea63f5", methods = "vote") // NeoToken
@Permission(contract = "*", methods = "transfer")
@DisplayName("GrantSharesTreasury")
@ManifestExtra(key = "author", value = "AxLabs")
public class GrantSharesTreasury {

    static final String OWNER_KEY = "owner";
    static final String FUNDERS_PREFIX = "funders";
    static final String WHITELISTED_TOKENS_PREFIX = "whitelistedTokens";
    static final String MULTI_SIG_THRESHOLD_KEY = "threshold";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap funders = new StorageMap(ctx, FUNDERS_PREFIX); // [hash, List<ECPoint>]
    static final StorageMap whitelistedTokens = new StorageMap(ctx, WHITELISTED_TOKENS_PREFIX); // [hash, max_amount]

    @DisplayName("FunderAdded")
    static Event1Arg<Hash160> funderAdded;
    @DisplayName("FunderRemoved")
    static Event1Arg<Hash160> funderRemoved;
    @DisplayName("WhitelistedTokenAdded")
    static Event2Args<Hash160, Integer> whitelistedTokenAdded;
    @DisplayName("WhitelistedTokenRemoved")
    static Event1Arg<Hash160> whitelistedTokenRemoved;
    @DisplayName("ThresholdChanged")
    static Event1Arg<Integer> thresholdChanged;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] config = (Object[]) data;
            // Set owner
            Storage.put(ctx, OWNER_KEY, (Hash160) config[0]);

            // Set initial funders.
            Object[][] accounts = (Object[][]) config[1]; // [  [hash, [keys...]],  [hash, [keys...]]  ]
            for (Object[] account : accounts) {
                Hash160 accountHash = (Hash160) account[0];
                ECPoint[] accountKeys = (ECPoint[]) account[1];
                funders.put(accountHash.toByteString(), StdLib.serialize(accountKeys));
            }

            // Set whitelisted tokens
            Map<Hash160, Integer> tokens = (Map<Hash160, Integer>) config[2];
            Hash160[] hashes = tokens.keys();
            Integer[] maxes = tokens.values();
            for (int i = 0; i < hashes.length; i++) {
                whitelistedTokens.put(hashes[i].toByteString(), maxes[i]);
            }

            // set parameter
            Storage.put(ctx, MULTI_SIG_THRESHOLD_KEY, (int) config[3]);
        }
    }

    /**
     * Checks if the sender is an eligible funder and if the transferred token is whitelisted.
     * <p>
     * Fails if the contract is paused.
     * <p>
     * If the received token is NEO, then a vote is cast on the committee member with the least votes.
     *
     * @param sender The token sender.
     * @param amount The transferred amount.
     * @param data   Data sent with the transfer.
     */
    @OnNEP17Payment
    public static void onNep17Payment(Hash160 sender, int amount, Object data) throws Exception {
        assertNotPaused();
        if (sender == null) {
            return; // If the sender is null the transfer is a GAS claim.
        }
        assert funders.get(sender.toByteString()) != null :
                "GrantSharesTreasury: Non-whitelisted sender";
        assert whitelistedTokens.get(Runtime.getCallingScriptHash().toByteString()) != null :
                "GrantSharesTreasury: Non-whitelisted token";

        if (Runtime.getCallingScriptHash() == NeoToken.getHash()) {
            voteCommitteeMemberWithLeastVotes();
        }
    }

    /**
     * Places the treasuries vote on the committee member with the least votes.
     *
     * @throws Exception if voting was not successful.
     */
    public static void voteCommitteeMemberWithLeastVotes() throws Exception {
        ECPoint c = getCommitteeMemberWithLeastVotes();
        if (!NeoToken.vote(Runtime.getExecutingScriptHash(), c)) {
            throw new Exception("Tried to vote on candidate " + c.toByteString().toString() + " but failed.");
        }
    }

    private static ECPoint getCommitteeMemberWithLeastVotes() {
        NeoToken.Candidate[] candidates = NeoToken.getCandidates();
        List<ECPoint> committee = new List<>(NeoToken.getCommittee());
        int leastVotes = 100000000; // just a large number for initialisation
        ECPoint lestVotesMember = null;
        for (int i = 0; i < candidates.length; i++) {
            NeoToken.Candidate candidate = candidates[i];
            for (int j = 0; j < committee.size(); j++) {
                if (committee.get(i) == candidate.publicKey) {
                    committee.remove(i); // Remove the committee member from list to shorten the for loop.
                    if (candidate.votes < leastVotes) {
                        lestVotesMember = candidate.publicKey;
                    }
                }
            }
        }
        return lestVotesMember;
    }

    /**
     * Gets all funders eligible to send funds to the treasury.
     *
     * @return the funder's addresses and their corresponding public keys.
     */
    @Safe
    public static Map<Hash160, ECPoint[]> getFunders() {
        Iterator<Struct<ByteString, ByteString>> it = funders.find(RemovePrefix);
        Map<Hash160, ECPoint[]> funders = new Map<>();
        while (it.next()) {
            Struct<ByteString, ByteString> entry = it.get();
            funders.put(new Hash160(entry.key), (ECPoint[]) StdLib.deserialize(entry.value));
        }
        return funders;
    }

    private static List<ECPoint> getFunderPublicKeys() {
        Iterator<ByteString> it = funders.find(ValuesOnly);
        List<ECPoint> pubKeys = new List<>();
        while (it.next()) {
            ECPoint[] keys = (ECPoint[]) StdLib.deserialize(it.get());
            for (ECPoint key : keys) {
                pubKeys.add(key);
            }
        }
        return pubKeys;
    }

    /**
     * Calculates the hash of the multi-sig address made up of the governance members. The signing threshold is
     * calculated from the value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of
     * public keys involved in the treasury as funders. This number can be higher than the number of funder
     * addresses because of possible multi-sig addresses.
     *
     * @return The multi-sig account hash.
     */
    @Safe
    public static Hash160 calcFundersMultiSigAddress() {
        List<ECPoint> funderPublicKeys = getFunderPublicKeys();
        return Account.createMultiSigAccount(calcFundersMultiSigAddressThreshold(funderPublicKeys.size()),
                funderPublicKeys.toArray());
    }

    /**
     * Calculates the threshold of the multi-sig address made up of the treasury funders. It is calculated from the
     * value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of public keys involved
     * in the treasury as funders. This number can be higher than the number of funder addresses because of
     * possible multi-sig addresses.
     *
     * @return The multi-sig account signing threshold.
     */
    @Safe
    public static int calcFundersMultiSigAddressThreshold() {
        return calcFundersMultiSigAddressThreshold(getFunderPublicKeys().size());
    }

    /**
     * Calculates the threshold of the multi-sig address made up of the treasury funders. It is calculated from the
     * value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the given number of involved public
     * keys.
     *
     * @param count The number of public keys involved in the treasury as funders.
     * @return The multi-sig account signing threshold.
     */
    private static int calcFundersMultiSigAddressThreshold(int count) {
        int thresholdRatio = Storage.getInt(ctx, MULTI_SIG_THRESHOLD_KEY);
        int thresholdTimes100 = count * thresholdRatio;
        int threshold = thresholdTimes100 / 100;
        if (thresholdTimes100 % 100 != 0) {
            threshold += 1; // Always round up.
        }
        return threshold;
    }

    /**
     * Gets all whitelisted tokens.
     *
     * @return a map of token hashes to the corresponding max funding amount set for the tokens.
     */
    @Safe
    public static Map<Hash160, Integer> getWhitelistedTokens() {
        Iterator<Struct<Hash160, Integer>> it = whitelistedTokens.find(RemovePrefix);
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
     * Gets the signing threshold ratio of the funders multi-sig account.
     *
     * @return the signing threshold ratio
     */
    @Safe
    public static int getFundersMultiSigThresholdRatio() {
        return Storage.getInt(ctx, MULTI_SIG_THRESHOLD_KEY);
    }

    /**
     * Sets the signing threshold ratio of the funders multi-sig account. The actual threshold is calculated from the
     * number of funder public keys times this ratio.
     * <p>
     * This method can only be called by the treasury owner and fails if the contract is paused.
     *
     * @param value The new threshold value.
     */
    public static void setFundersMultiSigThresholdRatio(Integer value) {
        assertNotPaused();
        assertCallerIsOwner();
        Storage.put(ctx, MULTI_SIG_THRESHOLD_KEY, value);
        thresholdChanged.fire(value);
    }


    /**
     * Adds the given account as a whitelisted funder to the treasury. Only whitelisted funders can transfer tokens to
     * the treasury. The public key(s) are necessary for the multi-sig address generated from all funders that can be
     * used to withdraw all treasury funds.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param accountHash The account to add to the funders list.
     * @param publicKeys  The public key(s) that are part of the account. One, in case of a single-sig account.
     *                    Multiple, in case of a multi-sig account.
     */
    public static void addFunder(Hash160 accountHash, ECPoint[] publicKeys) {
        assertNotPaused();
        assertCallerIsOwner();
        assert funders.get(accountHash.toByteString()) == null : "GrantSharesTreasury: Already a funder";
        funders.put(accountHash.toByteString(), StdLib.serialize(publicKeys));
        funderAdded.fire(accountHash);
    }

    /**
     * Removes the given account from the whitelisted funders.
     * <p>
     * This method must be called by the treasury owner and fails if the contract is paused.
     *
     * @param accountHash The funder account to remove.
     */
    public static void removeFunder(Hash160 accountHash) {
        assertNotPaused();
        assertCallerIsOwner();
        assert funders.get(accountHash.toByteString()) != null : "GrantSharesTreasury: Not a funder";
        funders.delete(accountHash.toByteString());
        funderRemoved.fire(accountHash);
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
        int maxFundingAmount = whitelistedTokens.getIntOrZero(tokenContract.toByteString());
        assert maxFundingAmount > 0 : "GrantSharesTreasury: Token not whitelisted";
        assert amount <= maxFundingAmount : "GrantSharesTreasury: Above token's max funding amount";
        Object[] params = new Object[]{
                Runtime.getExecutingScriptHash(), to, amount, new Object[]{}};
        boolean result = (boolean) Contract.call(tokenContract, "transfer", CallFlags.All, params);
        assert result : "GrantSharesTreasury: Failed releasing " + StdLib.itoa(amount, 10)
                + " tokens. Token contract: " + StdLib.base64Encode(tokenContract.toByteString());
    }

    /**
     * Drain the treasury contract to save the tokens from being stolen.
     * <p>
     * This method can only be called once the contract is paused and by the multi-sig account of
     * funders
     */
    public static void drain() {
        assert isPaused() : "GrantSharesTreasury: Contract is not paused";
        Hash160 fundersMultiAddress = calcFundersMultiSigAddress();
        assert checkWitness(fundersMultiAddress) : "GrantSharesTreasury: Not authorized";

        Hash160 selfHash = Runtime.getExecutingScriptHash();
        Iterator<ByteString> it = whitelistedTokens.find((byte) (RemovePrefix | KeysOnly));

        while (it.next()) {
            Hash160 token = new Hash160(it.get());
            int balance = (int) Contract.call(token, "balanceOf", CallFlags.ReadStates,
                    new Object[]{selfHash});
            if (balance > 0) {
                Object[] params = new Object[]{selfHash, fundersMultiAddress, balance,
                        new Object[]{}};
                Contract.call(token, "transfer", CallFlags.All, params);
            }
        }
    }

    /**
     * Updates the contract to the new NEF and manifest.
     * <p>
     * This method can only be called by the owner. It can be called even if the contract is paused
     * in case the contract needs a fix.
     */
    public static void updateContract(ByteString nef, String manifest, Object data) {
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