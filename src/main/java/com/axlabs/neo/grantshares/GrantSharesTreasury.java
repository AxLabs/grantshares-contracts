package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Account;
import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;

import static io.neow3j.devpack.Hash160.isValid;
import static io.neow3j.devpack.Hash160.zero;
import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.Runtime.getCallingScriptHash;
import static io.neow3j.devpack.Storage.getReadOnlyContext;
import static io.neow3j.devpack.constants.FindOptions.KeysOnly;
import static io.neow3j.devpack.constants.FindOptions.RemovePrefix;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;

@SuppressWarnings("unchecked")
@Permission(contract = "0xfffdc93764dbaddd97c48f252a53ea4643faa3fd", methods = "update") // ContractManagement
@Permission(contract = "0xef4073a0f2b305a38ec4050e4d3d28bc40ea63f5", methods = "vote") // NeoToken
@Permission(contract = "*", methods = "transfer")
@ManifestExtra(key = "Author", value = "AxLabs")
@ManifestExtra(key = "Email", value = "info@grantshares.io")
@ManifestExtra(key = "Description", value = "The treasury of the GrantShares DAO")
@ManifestExtra(key = "Website", value = "https://grantshares.io")
@ContractSourceCode("https://github.com/AxLabs/grantshares-contracts/blob/main/src/main/java/com/axlabs/neo/grantshares/GrantSharesTreasury.java")
@DisplayName("GrantSharesTreasury")
public class GrantSharesTreasury {

    static final String OWNER_KEY = "owner";
    static final String FUNDERS_PREFIX = "funders";
    static final String WHITELISTED_TOKENS_PREFIX = "whitelistedTokens";
    static final String MULTI_SIG_THRESHOLD_KEY = "threshold";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap funders = new StorageMap(ctx, FUNDERS_PREFIX); // [hash, List<ECPoint>]
    static final StorageMap whitelistedTokens = new StorageMap(ctx, WHITELISTED_TOKENS_PREFIX); // [hash, max_amount]

    //region EVENTS
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
    @DisplayName("TokenReleased")
    static Event3Args<Hash160, Hash160, Integer> tokensReleased;
    @DisplayName("TokenReleaseFailed")
    static Event3Args<Hash160, Hash160, Integer> releaseFailed;
    @DisplayName("VotedOnCommitteeMember")
    static Event1Arg<ECPoint> voted;
    @DisplayName("DrainedTokens")
    static Event drained;
    @DisplayName("DrainingTokenFailed")
    static Event1Arg<Hash160> drainingFailed;
    @DisplayName("UpdatingContract")
    static Event updating;
    @DisplayName("TokensReceived")
    static Event3Args<Hash160, Integer, Hash160> tokensReceived;
    @DisplayName("WhitelistedTokenMigrated")
    static Event2Args<Hash160, Integer> whitelistedTokenMigrated;
    //endregion EVENTS

    /**
     * Initialises this contract on deployment.
     * <p>
     * The data parameter has to be structured as follows:
     * <pre>
     * [
     *      Hash160 grantSharesGovHash,
     *      [
     *          Hash160 funderAddress1: [ECPoint funder1PublicKey1, ECPoint funder1PublicKey2, ...]
     *          Hash160 funderAddress2: [ECPoint funder2PublicKey1]
     *          ...
     *      ],
     *      [
     *          Hash160 tokenHash1: int maxFundingAmountToken1,
     *          Hash160 tokenHash2: int maxFundingAmountToken2
     *          ...
     *      ],
     *      int fundersMultiSigThresholdRatio
     * ]
     * </pre>
     *
     * @param data   The data to set up the treasury's storage with.
     * @param update Tells if the method is called for updating this contract.
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] config = (Object[]) data;
            // Set owner
            Hash160 ownerHash = (Hash160) config[0];
            assert isValid(ownerHash) && ownerHash != Hash160.zero();
            Storage.put(ctx, OWNER_KEY, ownerHash);

            // Set initial funders.
            Object[][] accounts = (Object[][]) config[1]; // [  [hash, [keys...]],  [hash, [keys...]]  ]
            for (Object[] account : accounts) {
                Hash160 accountHash = (Hash160) account[0];
                assert isValid(accountHash) && accountHash != Hash160.zero();
                ECPoint[] accountKeys = (ECPoint[]) account[1];
                for (ECPoint key : accountKeys) {
                    assert ECPoint.isValid(key);
                }
                funders.put(accountHash.toByteString(), new StdLib().serialize(accountKeys));
            }

            // Set whitelisted tokens
            Map<Hash160, Integer> tokens = (Map<Hash160, Integer>) config[2];
            Hash160[] hashes = tokens.keys();
            Integer[] maxes = tokens.values();
            for (int i = 0; i < hashes.length; i++) {
                assert isValid(hashes[i]) && hashes[i] != zero() && maxes[i] > 0;
                whitelistedTokens.put(hashes[i].toByteString(), maxes[i]);
            }

            // set parameter
            int thresholdRatio = (int) config[3];
            assert thresholdRatio > 0 && thresholdRatio <= 100;
            Storage.put(ctx, MULTI_SIG_THRESHOLD_KEY, thresholdRatio);
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
    public static void onNep17Payment(Hash160 sender, int amount, Object data) {
        assert !isPaused();
        assert whitelistedTokens.get(Runtime.getCallingScriptHash().toByteString()) != null;
        if (sender == null) {
            // Only allow new token minting from GasToken.
            assert getCallingScriptHash() == new GasToken().getHash();
            return;
        }
        assert funders.get(sender.toByteString()) != null;
        tokensReceived.fire(sender, amount, Runtime.getCallingScriptHash());
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
            funders.put(new Hash160(entry.key), (ECPoint[]) new StdLib().deserialize(entry.value));
        }
        return funders;
    }

    private static List<ECPoint> getFunderPublicKeys() {
        Iterator<ByteString> it = funders.find(ValuesOnly);
        List<ECPoint> pubKeys = new List<>();
        while (it.next()) {
            ECPoint[] keys = (ECPoint[]) new StdLib().deserialize(it.get());
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
    public static Hash160 calcFundersMultiSigAddress() throws Exception {
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
    public static int calcFundersMultiSigAddressThreshold() throws Exception {
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
    private static int calcFundersMultiSigAddressThreshold(int count) throws Exception {
        int thresholdRatio = Storage.getInt(getReadOnlyContext(), MULTI_SIG_THRESHOLD_KEY);
        int thresholdTimes100 = count * thresholdRatio;
        int threshold = thresholdTimes100 / 100;
        if (thresholdTimes100 % 100 != 0) {
            threshold += 1; // Always round up.
        }
        if (threshold == 0)
            throw new Exception("[GrantSharesTreasury.calcFundersMultiSigAddressThreshold] Threshold was zero");
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
        return (boolean) Contract.call(new Hash160(Storage.get(getReadOnlyContext(), OWNER_KEY)),
                "isPaused", CallFlags.ReadOnly, new Object[]{});
    }

    /**
     * Gets the signing threshold ratio of the funders multi-sig account.
     *
     * @return the signing threshold ratio
     */
    @Safe
    public static int getFundersMultiSigThresholdRatio() {
        return Storage.getInt(getReadOnlyContext(), MULTI_SIG_THRESHOLD_KEY);
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        if (value <= 0 || value > 100)
            Helper.abort("setFundersMultiSigThresholdRatio" + ": " + "Invalid threshold ratio");
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        if (funders.get(accountHash.toByteString()) != null) Helper.abort("addFunder" + ": " + "Already a funder");
        if (!isValid(accountHash) || accountHash == zero()) Helper.abort("addFunder" + ": " + "Invalid funder hash");
        if (publicKeys.length == 0) Helper.abort("addFunder" + ": " + "List of public keys is empty");
        for (ECPoint key : publicKeys) {
            if (!ECPoint.isValid(key)) Helper.abort("addFunder" + ": " + "Invalid public key");
        }
        funders.put(accountHash.toByteString(), new StdLib().serialize(publicKeys));
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        if (funders.get(accountHash.toByteString()) == null) Helper.abort("removeFunder" + ": " + "Not a funder");
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        if (!isValid(token) || token == zero()) Helper.abort("addWhitelistedToken" + ": " + "Invalid token hash");
        if (maxFundingAmount <= 0) Helper.abort("addWhitelistedToken" + ": " + "Invalid max funding amount");
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        if (whitelistedTokens.get(token.toByteString()) == null)
            Helper.abort("removeWhitelistedToken" + ": " + "Not a whitelisted token");
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
        abortIfPaused();
        abortIfCallerIsNotOwner();
        int maxFundingAmount = whitelistedTokens.getIntOrZero(tokenContract.toByteString());
        if (maxFundingAmount == 0) Helper.abort("releaseTokens" + ": " + "Token not whitelisted");
        if (amount > maxFundingAmount) Helper.abort("releaseTokens" + ": " + "Above token's max funding amount");
        Object[] params = new Object[]{Runtime.getExecutingScriptHash(), to, amount, new Object[]{}};
        boolean success = (boolean) Contract.call(tokenContract, "transfer", CallFlags.All, params);
        if (success) {
            tokensReleased.fire(tokenContract, to, amount);
        } else {
            releaseFailed.fire(tokenContract, to, amount);
        }
    }

    /**
     * Drain all tokens from the treasury contract to the funders multi-sig address.
     * <p>
     * This method can only be called by the funders multi-sig account when the {@link GrantSharesGov} contract is
     * paused.
     */
    public static void drain() {
        if (!isPaused()) Helper.abort("drain" + ": " + "Contract is not paused");
        Hash160 fundersMultiAddress = null;
        try {
            fundersMultiAddress = calcFundersMultiSigAddress();
        } catch (Exception e) {
            Helper.abort("drain" + ": " + e.getMessage());
        }
        if (!checkWitness(fundersMultiAddress)) Helper.abort("drain" + ": " + "Not authorized");
        Hash160 selfHash = Runtime.getExecutingScriptHash();
        Iterator<ByteString> it = whitelistedTokens.find((byte) (RemovePrefix | KeysOnly));
        while (it.next()) {
            Hash160 token = new Hash160(it.get());
            int balance = (int) Contract.call(token, "balanceOf", CallFlags.ReadOnly, new Object[]{selfHash});
            if (balance > 0) {
                Object[] params = new Object[]{selfHash, fundersMultiAddress, balance, new Object[]{}};
                try {
                    Contract.call(token, "transfer", CallFlags.All, params);
                } catch (Exception e) {
                    drainingFailed.fire(token);
                }
            }
        }
        drained.fire();
    }

    /**
     * Places the treasury's vote on the committee member with the least votes.
     */
    public static void voteCommitteeMemberWithLeastVotes() {
        abortIfPaused();
        ECPoint c = getCommitteeMemberWithLeastVotes();
        if (!new NeoToken().vote(Runtime.getExecutingScriptHash(), c))
            Helper.abort("voteCommitteeMemberWithLeastVotes" + ": " + "Failed voting on candidate");
        voted.fire(c);
    }

    private static ECPoint getCommitteeMemberWithLeastVotes() {
        NeoToken.Candidate[] candidates = new NeoToken().getCandidates();
        List<ECPoint> committee = new List<>(new NeoToken().getCommittee());
        int leastVotes = 100000000; // just a large number for initialisation
        ECPoint leastVotesMember = null;
        for (int i = 0; i < candidates.length; i++) {
            NeoToken.Candidate candidate = candidates[i];
            for (int j = 0; j < committee.size(); j++) {
                if (committee.get(j) == candidate.publicKey) {
                    committee.remove(j); // Remove the committee member from list to shorten the loop.
                    if (candidate.votes < leastVotes) {
                        leastVotesMember = candidate.publicKey;
                        leastVotes = candidate.votes;
                    }
                }
            }
        }
        return leastVotesMember;
    }

    /**
     * Updates the contract to the new NEF and manifest.
     * <p>
     * This method can only be called by the owner. It can be called even if the contract is paused
     * in case the contract needs a fix.
     */
    public static void updateContract(ByteString nef, String manifest, Object data) {
        abortIfPaused();
        abortIfCallerIsNotOwner();
        updating.fire();
        new ContractManagement().update(nef, manifest, data);
    }

    private static void abortIfCallerIsNotOwner() {
        if (Runtime.getCallingScriptHash().toByteString() != Storage.get(getReadOnlyContext(), OWNER_KEY))
            Helper.abort("abortIfCallerIsNotOwner" + ": " + "Not authorised");
    }

    private static void abortIfPaused() {
        if (isPaused()) Helper.abort("abortIfCallerIsNotOwner" + ": " + "Contract is paused");
    }
}
