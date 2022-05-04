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
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neow3j.devpack.Account.createStandardAccount;
import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.Runtime.getTime;
import static io.neow3j.devpack.Storage.getReadOnlyContext;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@Permission(contract = "*", methods = "*")
@ManifestExtra(key = "Author", value = "AxLabs")
@ManifestExtra(key = "Email", value = "info@grantshares.io")
@ManifestExtra(key = "Description", value = "The governing contract of the GrantShares DAO")
@ManifestExtra(key = "Website", value = "https://grantshares.io")
@ContractSourceCode("TODO: Set this to the URL of the release branch before deploying.")
@DisplayName("GrantSharesGov")
@SuppressWarnings("unchecked")
public class GrantSharesGov {

    //region CONTRACT VARIABLES

    // Parameter keys
    static final String REVIEW_LENGTH_KEY = "review_len"; // milliseconds
    static final String VOTING_LENGTH_KEY = "voting_len"; // milliseconds
    static final String TIMELOCK_LENGTH_KEY = "timelock_len"; // milliseconds
    static final String EXPIRATION_LENGTH_KEY = "expiration_len"; // milliseconds
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate"; // percentage
    static final String MIN_QUORUM_KEY = "min_quorum"; // percentage
    static final String MULTI_SIG_THRESHOLD_KEY = "threshold"; // percentage

    static final String PROPOSALS_COUNT_KEY = "#_proposals"; //int
    static final String PAUSED_KEY = "paused"; // boolean
    static final String MEMBERS_COUNT_KEY = "#_members"; // int

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = new StorageMap(ctx, 1); // [int id: Proposal proposal]
    static final StorageMap proposalData = new StorageMap(ctx, 2); // [int id: ProposalData proposalData]
    static final StorageMap proposalVotes = new StorageMap(ctx, 3); // [int id: ProposalVotes proposalVotes]
    static final StorageMap parameters = new StorageMap(ctx, 4); // [String param_key: int param_value ]
    static final byte MEMBERS_MAP_PREFIX = 5;
    static final StorageMap members = new StorageMap(ctx, MEMBERS_MAP_PREFIX); // [Hash160 accHash: ECPoint publicKey]
    //endregion CONTRACT VARIABLES

    //region EVENTS
    @DisplayName("ProposalCreated")
    static Event4Args<Integer, Hash160, Integer, Integer> created;
    @DisplayName("ProposalEndorsed")
    static Event2Args<Integer, Hash160> endorsed;
    @DisplayName("Voted")
    static Event3Args<Integer, Hash160, Integer> voted;
    @DisplayName("ProposalExecuted")
    static Event1Arg<Integer> executed;
    @DisplayName("MemberAdded")
    static Event1Arg<Hash160> memberAdded;
    @DisplayName("MemberRemoved")
    static Event1Arg<Hash160> memberRemoved;
    @DisplayName("ParameterChanged")
    static Event2Args<String, Integer> paramChanged;
    @DisplayName("UpdatingContract")
    static Event updating;
    @DisplayName("ContractPaused")
    static Event paused;
    @DisplayName("ContractUnpaused")
    static Event unpaused;
    @DisplayName("ProposalMigrated")
    static Event1Arg<Integer> migrated;
    @DisplayName("Error")
    static Event2Args<String, String> error;
    //endregion EVENTS

    /**
     * Initialises this contract on deployment.
     * <p>
     * The data parameter has to be structured as follows:
     * <pre>
     * [
     *      [
     *          ECPoint publicKeyMember1,
     *          ECPoint publicKeyMember2,
     *          ...
     *      ],
     *      [
     *          String paramName1: int paramValue1,
     *          String paramName2: int paramValue2,
     *          ...
     *      ],
     * ]
     * </pre>
     *
     * @param data   The data to set up the governance's storage with.
     * @param update Tells if the method is called for updating this contract.
     */
    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            List<Object> config = (List<Object>) data;
            // Set parameters
            List<Object> params = (List<Object>) config.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                String paramKey = (String) params.get(i);
                int value = (int) params.get(i + 1);
                throwOnInvalidValue(paramKey, value);
                parameters.put(paramKey, value);
            }

            // Set members
            ECPoint[] pubKeys = (ECPoint[]) config.get(0);
            assert pubKeys.length != 0;
            for (ECPoint pubKey : pubKeys) {
                assert ECPoint.isValid(pubKey);
                members.put(createStandardAccount(pubKey).toByteString(), pubKey.toByteString());
            }

            Storage.put(ctx, MEMBERS_COUNT_KEY, pubKeys.length);
            Storage.put(ctx, PAUSED_KEY, 0);
            Storage.put(ctx, PROPOSALS_COUNT_KEY, 0);
        } else {
            Iterator<Struct<ByteString, ByteString>> it = proposalData.find(FindOptions.RemovePrefix);
            while (it.next()) {
                Struct<ByteString, ByteString> item = it.get();
                int proposalId = item.key.toInt();
                ProposalDataOld pd = (ProposalDataOld) deserialize(item.value);
                List<Intent> newIntents = new List<>();
                for (IntentOld intent : pd.intents) {
                    newIntents.add(new Intent(intent.targetContract, intent.method, intent.params, CallFlags.All));
                }
                ProposalData pdn = new ProposalData(pd.proposer, pd.linkedProposal, pd.acceptanceRate,
                        pd.quorum, newIntents.toArray(), pd.offchainUri);
                proposalData.put(proposalId, serialize(pdn));
                migrated.fire(proposalId);
            }
        }
    }

    //region SAFE METHODS

    /**
     * Gets the value of the parameter with {@code paramName}.
     *
     * @param paramName The name of the parameter, which is also its storage key.
     * @return the parameter's value.
     */
    @Safe
    public static Object getParameter(String paramName) {
        return parameters.get(paramName);
    }

    /**
     * @return all parameters and their values.
     */
    @Safe
    public static Map<String, Object> getParameters() {
        Iterator<Struct<String, Object>> it = parameters.find(FindOptions.RemovePrefix);
        Map<String, Object> params = new Map<>();
        while (it.next()) {
            Struct<String, Object> param = it.get();
            params.put(param.key, param.value);
        }
        return params;
    }

    /**
     * Gets all information of the proposal with {@code id}.
     *
     * @param id The proposal's id.
     * @return the proposal.
     */
    @Safe
    public static ProposalDTO getProposal(int id) {
        ProposalDTO dto = new ProposalDTO();
        dto.id = id;
        ByteString bytes = proposalData.get(id);
        if (bytes != null) {
            ProposalData p = (ProposalData) deserialize(bytes);
            dto.proposer = p.proposer;
            dto.linkedProposal = p.linkedProposal;
            dto.acceptanceRate = p.acceptanceRate;
            dto.quorum = p.quorum;
            dto.intents = p.intents;
            dto.offchainUri = p.offchainUri;
        } else {
            return null;
        }
        bytes = proposals.get(id);
        if (bytes != null) {
            Proposal p = (Proposal) deserialize(bytes);
            dto.endorser = p.endorser;
            dto.reviewEnd = p.reviewEnd;
            dto.votingEnd = p.votingEnd;
            dto.queuedEnd = p.timeLockEnd;
            dto.expiration = p.expiration;
            dto.executed = p.executed;
        }
        bytes = proposalVotes.get(id);
        if (bytes != null) {
            ProposalVotes p = (ProposalVotes) deserialize(bytes);
            dto.approve = p.approve;
            dto.reject = p.reject;
            dto.abstain = p.abstain;
            dto.voters = p.voters;
        }
        return dto;
    }

    /**
     * Returns the public keys of the governance members.
     * <p>
     * The ordering of the returned list can change for consecutive calls.
     *
     * @return the members.
     */
    @Safe
    public static List<ECPoint> getMembers() {
        Iterator<ByteString> it = members.find(ValuesOnly);
        List<ECPoint> members = new List<>();
        while (it.next()) {
            members.add(new ECPoint(it.get()));
        }
        return members;
    }

    /**
     * Gets the number of members.
     *
     * @return the number of members.
     */
    @Safe
    public static int getMembersCount() {
        return Storage.getInt(getReadOnlyContext(), MEMBERS_COUNT_KEY);
    }

    /**
     * Gets the number of proposals created on this contract.
     *
     * @return the number of proposals.
     */
    @Safe
    public static int getProposalCount() {
        return Storage.getInt(getReadOnlyContext(), PROPOSALS_COUNT_KEY);
    }

    /**
     * Gets the proposals on the given page.
     *
     * @param page         The page.
     * @param itemsPerPage The number of proposals per page.
     * @return the chosen page, how many pages ther are with the given page size and the found
     * proposals on the given page.
     */
    @Safe
    public static Paginator.Paginated getProposals(int page, int itemsPerPage) throws Exception {
        if (page < 0)
            throw new Exception("[GrantSharesGov.getProposals] Page number was negative");
        if (itemsPerPage <= 0)
            throw new Exception("[GrantSharesGov.getProposals] Page number was negative or zero");
        int n = Storage.getInt(getReadOnlyContext(), PROPOSALS_COUNT_KEY);
        int[] pagination = Paginator.calcPagination(n, page, itemsPerPage);
        List<Object> list = new List<>();
        for (int i = pagination[0]; i < pagination[1]; i++) {
            list.add(getProposal(i));
        }
        return new Paginator.Paginated(page, pagination[2], list);
    }

    /**
     * Checks if the contract is paused, i.e., if the corresponding value in the contract storage is
     * set to true.
     *
     * @return true if the contract is paused. False otherwise.
     */
    @Safe
    public static boolean isPaused() {
        return Storage.getBoolean(getReadOnlyContext(), PAUSED_KEY);
    }

    /**
     * Calculates the hash of the multi-sig account made up of the governance members. The signing threshold is
     * calculated from the value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of
     * members.
     *
     * @return The multi-sig account hash.
     */
    @Safe
    public static Hash160 calcMembersMultiSigAccount() throws Exception {
        return Account.createMultiSigAccount(calcMembersMultiSigAccountThreshold(), getMembers().toArray());
    }

    /**
     * Calculates the threshold of the multi-sig account made up of the governance members. It is calculated from the
     * value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of members.
     *
     * @return The multi-sig account signing threshold.
     */
    @Safe
    public static int calcMembersMultiSigAccountThreshold() throws Exception {
        int count = Storage.getInt(getReadOnlyContext(), MEMBERS_COUNT_KEY);
        int thresholdRatio = parameters.getInt(MULTI_SIG_THRESHOLD_KEY);
        int thresholdTimes100 = count * thresholdRatio;
        int threshold = thresholdTimes100 / 100;
        if (thresholdTimes100 % 100 != 0) {
            threshold += 1; // Always round up.
        }
        if (threshold == 0)
            throw new Exception("[GrantSharesGov.calcMembersMultiSigAccountThreshold] Threshold was zero");
        return threshold;
    }

    //endregion SAFE METHODS

    // region GOVERNANCE PROCESS METHODS

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer    The account set as the proposer.
     * @param intents     The intents to be executed when the proposal is accepted.
     * @param offchainUri The URI of the part of the proposal that exists off-chain. E.g., a unique discussion URL.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents, String offchainUri, int linkedProposal) {
        return createProposal(proposer, intents, offchainUri, linkedProposal,
                parameters.getInt(MIN_ACCEPTANCE_RATE_KEY),
                parameters.getInt(MIN_QUORUM_KEY));
    }

    /**
     * Creates a proposal.
     *
     * @param proposer       The account set as the proposer.
     * @param intents        The intents to be executed when the proposal is accepted.
     * @param offchainUri    The URI of the part of the proposal that exists off-chain. E.g., a unique discussion URL.
     * @param linkedProposal A proposal that preceded this one.
     * @param acceptanceRate The desired acceptance rate.
     * @param quorum         The desired quorum.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents, String offchainUri, int linkedProposal,
            int acceptanceRate, int quorum) {

        if (!checkWitness(proposer)) fireErrorAndAbort("Not authorised", "createProposal");
        if (acceptanceRate < parameters.getInt(MIN_ACCEPTANCE_RATE_KEY) || acceptanceRate > 100)
            fireErrorAndAbort("Invalid acceptance rate", "createProposal");
        if (quorum < parameters.getInt(MIN_QUORUM_KEY) || quorum > 100)
            fireErrorAndAbort("Invalid quorum", "createProposal");
        if (linkedProposal >= 0 && proposals.get(linkedProposal) == null)
            fireErrorAndAbort("Linked proposal doesn't exist", "createProposal");
        if (containsCallsToContractManagement(intents))
            fireErrorAndAbort("Calls to ContractManagement not allowed", "createProposal");
        if (!areIntentsValid(intents)) fireErrorAndAbort("Invalid intents", "createProposal");

        int id = Storage.getInt(getReadOnlyContext(), PROPOSALS_COUNT_KEY);
        int expiration = parameters.getInt(EXPIRATION_LENGTH_KEY) + getTime();
        proposals.put(id, serialize(new Proposal(id, expiration)));
        proposalData.put(id, serialize(new ProposalData(proposer, linkedProposal, acceptanceRate,
                quorum, intents, offchainUri)));
        proposalVotes.put(id, serialize(new ProposalVotes()));
        Storage.put(ctx, PROPOSALS_COUNT_KEY, id + 1);

        // An event can take max 1024 bytes data. Thus, we're not passing the offchainUri since it could be longer.
        created.fire(id, proposer, acceptanceRate, quorum);
        return id;
    }

    private static boolean areIntentsValid(Intent[] intents) {
        for (Intent intent : intents) {
            if (!Hash160.isValid(intent.targetContract) ||
                    intent.targetContract == Hash160.zero() ||
                    intent.method == null ||
                    intent.method == "" ||
                    !Helper.within(intent.callFlags, 1, 256)
            ) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsCallsToContractManagement(Intent[] intents) {
        for (Intent i : intents) {
            if (i.targetContract == ContractManagement.getHash()) {
                return true;
            }
        }
        return false;
    }

    /**
     * If the {@code endorser} is a DAO member and the proposal is an existing un-endorsed
     * proposal, it becomes endorsed and its phases are set.
     *
     * @param id       The ID of the proposal to endorse.
     * @param endorser The script hash of the endorsing DAO member.
     */
    public static void endorseProposal(int id, Hash160 endorser) {
        abortIfPaused();
        if (members.get(endorser.toByteString()) == null || !checkWitness(endorser))
            fireErrorAndAbort("Not authorised", "endorseProposal");
        ByteString proposalBytes = proposals.get(id);
        if (proposalBytes == null) fireErrorAndAbort("Proposal doesn't exist", "endorseProposal");
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        if (proposal.expiration <= getTime()) fireErrorAndAbort("Proposal expired", "endorseProposal");
        if (proposal.endorser != null) fireErrorAndAbort("Proposal already endorsed", "endorseProposal");

        proposal.endorser = endorser;
        proposal.reviewEnd = getTime() + parameters.getInt(REVIEW_LENGTH_KEY);
        proposal.votingEnd = proposal.reviewEnd + parameters.getInt(VOTING_LENGTH_KEY);
        proposal.timeLockEnd = proposal.votingEnd + parameters.getInt(TIMELOCK_LENGTH_KEY);
        proposal.expiration = proposal.timeLockEnd + parameters.getInt(EXPIRATION_LENGTH_KEY);
        proposals.put(id, serialize(proposal));
        endorsed.fire(id, endorser);
    }

    /**
     * Casts a vote of the {@code voter} on the proposal with {@code id}.
     *
     * @param id    The id of the proposal to vote on.
     * @param vote  The vote. Must be either -1 for rejecting, 1 for approving or 0 for
     *              abstaining.
     * @param voter The script hash of the voter. Must be a member of the DAO and the
     *              invoking script must hold a witness of the voter.
     */
    public static void vote(int id, int vote, Hash160 voter) {
        abortIfPaused();
        if (vote < -1 || vote > 1) fireErrorAndAbort("Invalid vote", "vote");
        if (members.get(voter.toByteString()) == null || !checkWitness(voter))
            fireErrorAndAbort("Not authorised", "vote");
        ByteString proposalBytes = proposals.get(id);
        if (proposalBytes == null) fireErrorAndAbort("Proposal doesn't exist", "vote");
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        int time = getTime();
        if (proposal.endorser == null || time < proposal.reviewEnd || time >= proposal.votingEnd)
            fireErrorAndAbort("Proposal not active", "vote");
        ProposalVotes pv = (ProposalVotes) deserialize(proposalVotes.get(id));
        if (pv.voters.containsKey(voter)) fireErrorAndAbort("Already voted on this proposal", "vote");

        pv.voters.put(voter, vote);
        if (vote < 0) {
            pv.reject += 1;
        } else if (vote > 0) {
            pv.approve += 1;
        } else {
            pv.abstain += 1;
        }
        proposalVotes.put(id, serialize(pv));
        voted.fire(id, voter, vote);
    }

    /**
     * Executes the proposal with the given {@code id}. Anyone can execute any proposal.
     * <p>
     * Execution is only successful if the proposal is out of its queued phase, was accepted and
     * does not have a connected abrogation proposal that was accepted.
     *
     * @param id The proposal id.
     * @return the values returned by the proposal's intents.
     */
    public static Object[] execute(int id) {
        abortIfPaused();
        ByteString proposalBytes = proposals.get(id);
        if (proposalBytes == null) fireErrorAndAbort("Proposal doesn't exist", "execute");
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        if (proposal.endorser == null || getTime() < proposal.timeLockEnd)
            fireErrorAndAbort("Proposal not in execution phase", "execute");
        if (proposal.executed) fireErrorAndAbort("Proposal already executed", "execute");
        if (proposal.expiration <= getTime()) fireErrorAndAbort("Proposal expired", "execute");
        ProposalData data = (ProposalData) deserialize(proposalData.get(id));
        ProposalVotes votes = (ProposalVotes) deserialize(proposalVotes.get(id));
        int voteCount = votes.approve + votes.abstain + votes.reject;
        if (voteCount * 100 / Storage.getInt(getReadOnlyContext(), MEMBERS_COUNT_KEY) < data.quorum)
            fireErrorAndAbort("Quorum not reached", "execute");
        int yesNoCount = votes.approve + votes.reject;
        if (votes.approve * 100 / yesNoCount <= data.acceptanceRate) fireErrorAndAbort("Proposal rejected", "execute");

        proposal.executed = true;
        Object[] returnVals = new Object[data.intents.length];
        proposals.put(id, serialize(proposal));
        for (int i = 0; i < data.intents.length; i++) {
            Intent t = data.intents[i];
            returnVals[i] = Contract.call(t.targetContract, t.method, t.callFlags, t.params);
        }
        executed.fire(id);
        return returnVals;
    }
    // endregion GOVERNANCE PROCESS METHODS

    //region PROPOSAL-INVOKED METHODS

    /**
     * Changes the value of the parameter with {@code paramKey} to {@code value}.
     * <p>
     * This method can only be called by the contract itself.
     *
     * @param paramKey The parameter's storage key.
     * @param value    The new parameter value.
     */
    public static void changeParam(String paramKey, int value) {
        abortIfPaused();
        abortIfCallerIsNotSelf();
        throwOnInvalidValue(paramKey, value);
        parameters.put(paramKey, value);
        paramChanged.fire(paramKey, value);
    }

    private static void throwOnInvalidValue(String paramKey, int value) {
        switch (paramKey) {
            case REVIEW_LENGTH_KEY:
            case VOTING_LENGTH_KEY:
            case TIMELOCK_LENGTH_KEY:
            case EXPIRATION_LENGTH_KEY:
                if (value < 0) fireErrorAndAbort("Invalid parameter value", "changeParam");
                break;
            case MIN_ACCEPTANCE_RATE_KEY:
            case MIN_QUORUM_KEY:
                if (value < 0 || value > 100) fireErrorAndAbort("Invalid parameter value", "changeParam");
                break;
            case MULTI_SIG_THRESHOLD_KEY:
                if (value <= 0 || value > 100) fireErrorAndAbort("Invalid parameter value", "changeParam");
                break;
            default:
                fireErrorAndAbort("Unknown parameter", "changeParam");
        }
    }

    /**
     * Adds the account with the given public key as a new member.
     * <p>
     * This method can only be called by the contract itself.
     *
     * @param memberPubKey The new member's public key.
     */
    public static void addMember(ECPoint memberPubKey) {
        abortIfPaused();
        abortIfCallerIsNotSelf();
        Hash160 memberHash = createStandardAccount(memberPubKey);
        if (members.get(memberHash.toByteString()) != null) fireErrorAndAbort("Already a member", "addMember");
        members.put(memberHash.toByteString(), memberPubKey.toByteString());
        Storage.put(ctx, MEMBERS_COUNT_KEY, Storage.getInt(getReadOnlyContext(), MEMBERS_COUNT_KEY) + 1);
        memberAdded.fire(memberHash);
    }

    /**
     * Removes the account with the given public key from the list of members.
     * <p>
     * This method can only be called by the contract itself.
     *
     * @param memberPubKey The member to remove.
     */
    public static void removeMember(ECPoint memberPubKey) {
        abortIfPaused();
        abortIfCallerIsNotSelf();
        Hash160 memberHash = createStandardAccount(memberPubKey);
        if (members.get(memberHash.toByteString()) == null) fireErrorAndAbort("Not a member", "removeMember");
        members.delete(memberHash.toByteString());
        Storage.put(ctx, MEMBERS_COUNT_KEY, Storage.getInt(getReadOnlyContext(), MEMBERS_COUNT_KEY) - 1);
        memberRemoved.fire(memberHash);
    }

    /**
     * Updates the contract to the new NEF and manifest.
     * <p>
     * This method can only be called by the contract itself. It can be called even if the
     * contract is paused in case the contract needs a fix.
     *
     * @param nef      The new contract NEF.
     * @param manifest The new contract manifest.
     * @param data     Optional data passed to the update (_deploy) method.
     */
    public static void updateContract(ByteString nef, String manifest, Object data) {
        abortIfPaused();
        abortIfCallerIsNotSelf();
        updating.fire();
        ContractManagement.update(nef, manifest, data);
    }
    //endregion PROPOSAL-INVOKED METHODS

    public static void pause() {
        Hash160 membersMultiSigHash = null;
        try {
            membersMultiSigHash = calcMembersMultiSigAccount();
        } catch (Exception e) {
            fireErrorAndAbort(e.getMessage(), "pause");
        }
        if (!checkWitness(membersMultiSigHash)) fireErrorAndAbort("Not authorized", "pause");
        Storage.put(ctx, PAUSED_KEY, 1);
        paused.fire();
    }

    public static void unpause() {
        Hash160 membersMultiSigHash = null;
        try {
            membersMultiSigHash = calcMembersMultiSigAccount();
        } catch (Exception e) {
            fireErrorAndAbort(e.getMessage(), "pause");
        }
        if (!checkWitness(membersMultiSigHash)) fireErrorAndAbort("Not authorized", "unpause");
        Storage.put(ctx, PAUSED_KEY, 0);
        unpaused.fire();
    }

    private static void abortIfCallerIsNotSelf() {
        if (Runtime.getCallingScriptHash() != Runtime.getExecutingScriptHash()) {
            fireErrorAndAbort("Method only callable by the contract itself", "abortIfCallerIsNotSelf");
        }
    }

    public static void abortIfPaused() {
        if (Storage.getBoolean(getReadOnlyContext(), PAUSED_KEY)) {
            fireErrorAndAbort("Contract is paused", "abortIfPaused");
        }
    }

    private static void fireErrorAndAbort(String msg, String method) {
        error.fire(msg, method);
        Helper.abort();
    }

}