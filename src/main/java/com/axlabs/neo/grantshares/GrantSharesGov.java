package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Account;
import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.StringLiteralHelper;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neow3j.devpack.Account.createStandardAccount;
import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;
import static io.neow3j.devpack.contracts.LedgerContract.currentIndex;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@Permission(contract = "*", methods = "*")
@DisplayName("GrantSharesGov")
@ManifestExtra(key = "author", value = "AxLabs")
@SuppressWarnings("unchecked")
public class GrantSharesGov {

    //region CONTRACT VARIABLES

    // Parameter keys
    static final String REVIEW_LENGTH_KEY = "review_len"; // milliseconds
    static final String VOTING_LENGTH_KEY = "voting_len"; // milliseconds
    static final String TIMELOCK_LENGTH_KEY = "timelock_len"; // milliseconds
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate"; // percentage
    static final String MIN_QUORUM_KEY = "min_quorum"; // percentage
    static final String MULTI_SIG_THRESHOLD_KEY = "threshold"; // percentage

    static final String PROPOSALS_COUNT_KEY = "#_proposals"; //int
    static final String PAUSED_KEY = "paused"; // boolean
    static final String MEMBERS_COUNT_KEY = "#_members"; // int

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = new StorageMap(ctx, 1); // [id, Proposal]
    static final StorageMap proposalData = new StorageMap(ctx, 2); // [id, ProposalData]
    static final StorageMap proposalVotes = new StorageMap(ctx, 3); // [id, ProposalVotes]
    static final StorageMap parameters = new StorageMap(ctx, 4); // [param_key, param_value]
    static final byte MEMBERS_MAP_PREFIX = 5;
    static final StorageMap members = new StorageMap(ctx, MEMBERS_MAP_PREFIX); // [Hash160, ECPoint]
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
    static Event2Args<String, byte[]> paramChanged;
    //endregion EVENTS

    @OnDeployment
    @SuppressWarnings("unchecked")
    public static void deploy(Object data, boolean update) {
        if (!update) {
            List<Object> config = (List<Object>) data;
            // Set parameters
            List<Object> params = (List<Object>) config.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                parameters.put((ByteString) params.get(i), (int) params.get(i + 1));
            }

            // Set members
            ECPoint[] pubKeys = (ECPoint[]) config.get(0);
            for (ECPoint pubKey : pubKeys) {
                members.put(createStandardAccount(pubKey).toByteString(), pubKey.toByteString());
            }
            Storage.put(ctx, MEMBERS_COUNT_KEY, pubKeys.length);
            Storage.put(ctx, PAUSED_KEY, 0);
            Storage.put(ctx, PROPOSALS_COUNT_KEY, 0);
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
            dto.discussionUrl = p.discussionUrl;
        } else {
            return null;
        }
        bytes = proposals.get(id);
        if (bytes != null) {
            Proposal p = (Proposal) deserialize(bytes);
            dto.endorser = p.endorser;
            dto.reviewEnd = p.reviewEnd;
            dto.votingEnd = p.votingEnd;
            dto.queuedEnd = p.queuedEnd;
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
        return Storage.getInt(ctx, MEMBERS_COUNT_KEY);
    }

    /**
     * Gets the number of proposals created on this contract.
     *
     * @return the number of proposals.
     */
    @Safe
    public static int getProposalCount() {
        return Storage.getInt(ctx, PROPOSALS_COUNT_KEY);
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
    public static Paginator.Paginated getProposals(int page, int itemsPerPage) {
        int n = Storage.getInt(ctx, PROPOSALS_COUNT_KEY);
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
        return Storage.get(ctx, PAUSED_KEY) == StringLiteralHelper.hexToBytes("01");
    }


    /**
     * Calculates the hash of the multi-sig account made up of the governance members. The signing threshold is
     * calculated from the value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of
     * members.
     *
     * @return The multi-sig account hash.
     */
    @Safe
    public static Hash160 calcMembersMultiSigAccount() {
        return Account.createMultiSigAccount(calcMembersMultiSigAccountThreshold(), getMembers().toArray());
    }

    /**
     * Calculates the threshold of the multi-sig account made up of the governance members. It is calculated from the
     * value of the {@link GrantSharesGov#MULTI_SIG_THRESHOLD_KEY} parameter and the number of members.
     *
     * @return The multi-sig account signing threshold.
     */
    @Safe
    public static int calcMembersMultiSigAccountThreshold() {
        int count = Storage.getInt(ctx, MEMBERS_COUNT_KEY);
        int thresholdRatio = parameters.getInt(MULTI_SIG_THRESHOLD_KEY);
        int thresholdTimes100 = count * thresholdRatio;
        int threshold = thresholdTimes100 / 100;
        if (thresholdTimes100 % 100 != 0) {
            threshold += 1; // Always round up.
        }
        return threshold;
    }

    //endregion SAFE METHODS

    // region GOVERNANCE PROCESS METHODS

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer      The account set as the proposer.
     * @param intents       The intents to be executed when the proposal is accepted.
     * @param discussionUrl The proposal's discussion URL. Must not be larger than 1024 bytes.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents, String discussionUrl,
            int linkedProposal) {

        return createProposal(proposer, intents, discussionUrl, linkedProposal,
                parameters.getInt(MIN_ACCEPTANCE_RATE_KEY),
                parameters.getInt(MIN_QUORUM_KEY));
    }

    /**
     * Creates a proposal.
     *
     * @param proposer       The account set as the proposer.
     * @param intents        The intents to be executed when the proposal is accepted.
     * @param discussionUrl  The proposal discussion URL. Must not be larger than 1024 bytes.
     * @param linkedProposal A proposal that preceded this one.
     * @param acceptanceRate The desired acceptance rate.
     * @param quorum         The desired quorum.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents, String discussionUrl,
            int linkedProposal, int acceptanceRate, int quorum) {

        assert checkWitness(proposer) : "GrantSharesGov: Not authorised";
        assert acceptanceRate >= parameters.getInt(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesGov: Acceptance rate not allowed";
        assert quorum >= parameters.getInt(MIN_QUORUM_KEY)
                : "GrantSharesGov: Quorum not allowed";
        assert linkedProposal < 0 || proposals.get(linkedProposal) != null
                : "GrantSharesGov: Linked proposal doesn't exist";

        int id = Storage.getInt(ctx, PROPOSALS_COUNT_KEY);
        proposals.put(id, serialize(new Proposal(id)));
        proposalData.put(id, serialize(new ProposalData(proposer, linkedProposal, acceptanceRate,
                quorum, intents, discussionUrl)));
        proposalVotes.put(id, serialize(new ProposalVotes()));
        Storage.put(ctx, PROPOSALS_COUNT_KEY, id + 1);

        // An event can take max 1024 bytes state data. Thus, we're not passing the discussionUrl.
        created.fire(id, proposer, acceptanceRate, quorum);
        return id;
    }

    /**
     * If the {@code endorser} is a DAO member and the proposal is an existing un-endorsed
     * proposal, it becomes endorsed and its phases are set.
     *
     * @param id       The ID of the proposal to endorse.
     * @param endorser The script hash of the endorsing DAO member.
     */
    public static void endorseProposal(int id, Hash160 endorser) {
        assert members.get(endorser.toByteString()) != null && checkWitness(endorser)
                : "GrantSharesGov: Not authorised";
        ByteString proposalBytes = proposals.get(id);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser == null : "GrantSharesGov: Proposal already endorsed";
        proposal.endorser = endorser;

        // TODO: When deploying the contract, `currentIndex()+1` has to be replaced with getTime().
        //  For testing purposes we're using the block number for the phase length but in production
        //  the contract should use the actual time.
        proposal.reviewEnd = currentIndex() + 1 + parameters.getInt(REVIEW_LENGTH_KEY);
        proposal.votingEnd = proposal.reviewEnd + parameters.getInt(VOTING_LENGTH_KEY);
        proposal.queuedEnd = proposal.votingEnd + parameters.getInt(TIMELOCK_LENGTH_KEY);
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
        assert vote >= -1 && vote <= 1 : "GrantSharesGov: Invalid vote";
        assert members.get(voter.toByteString()) != null && checkWitness(voter)
                : "GrantSharesGov: Not authorised";
        ByteString proposalBytes = proposals.get(id);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser != null : "GrantSharesGov: Proposal wasn't endorsed";
        // TODO: `currentIndex` has to be replaced with `getTime`
        int time = currentIndex();
        assert time >= proposal.reviewEnd && time < proposal.votingEnd
                : "GrantSharesGov: Proposal not active";

        // No need for null check. This map was created in the endorsement, and we know the
        // endorsement happened because the proposal phases were set.
        ProposalVotes pv = (ProposalVotes) deserialize(proposalVotes.get(id));
        assert !pv.voters.containsKey(voter) : "GrantSharesGov: Already voted on this proposal";
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
        ByteString proposalBytes = proposals.get(id);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser != null : "GrantSharesGov: Proposal wasn't endorsed yet";
        // TODO: `currentIndex` has to be replaced with `getTime`
        assert currentIndex() >= proposal.queuedEnd
                : "GrantSharesGov: Proposal not in execution phase";
        assert !proposal.executed : "GrantSharesGov: Proposal already executed";
        ProposalData data = (ProposalData) deserialize(proposalData.get(id));
        ProposalVotes votes = (ProposalVotes) deserialize(proposalVotes.get(id));
        int voteCount = votes.approve + votes.abstain + votes.reject;
        assert voteCount * 100 / Storage.getInt(ctx, MEMBERS_COUNT_KEY) >= data.quorum
                : "GrantSharesGov: Quorum not reached";
        int yesNoCount = votes.approve + votes.reject;
        assert votes.approve * 100 / yesNoCount > data.acceptanceRate
                : "GrantSharesGov: Proposal rejected";

        proposal.executed = true;
        Object[] returnVals = new Object[data.intents.length];
        proposals.put(id, serialize(proposal));
        for (int i = 0; i < data.intents.length; i++) {
            Intent t = data.intents[i];
            returnVals[i] = Contract.call(t.targetContract, t.method, CallFlags.All, t.params);
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
    public static void changeParam(String paramKey, Object value) {
        assertNotPaused();
        assertCallerIsSelf();
        parameters.put(paramKey, (byte[]) value);
        paramChanged.fire(paramKey, (byte[]) value);
    }

    /**
     * Adds the account with the given public key as a new member.
     * <p>
     * This method can only be called by the contract itself.
     *
     * @param memberPubKey The new member's public key.
     */
    public static void addMember(ECPoint memberPubKey) {
        assertNotPaused();
        assertCallerIsSelf();
        Hash160 memberHash = createStandardAccount(memberPubKey);
        assert members.get(memberHash.toByteString()) == null : "GrantSharesGov: Already a member";
        members.put(memberHash.toByteString(), memberPubKey.toByteString());
        Storage.put(ctx, MEMBERS_COUNT_KEY, Storage.getInt(ctx, MEMBERS_COUNT_KEY) + 1);
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
        assertNotPaused();
        assertCallerIsSelf();
        Hash160 memberHash = createStandardAccount(memberPubKey);
        assert members.get(memberHash.toByteString()) != null : "GrantSharesGov: Not a member";
        members.delete(memberHash.toByteString());
        Storage.put(ctx, MEMBERS_COUNT_KEY, Storage.getInt(ctx, MEMBERS_COUNT_KEY) - 1);
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
        assertCallerIsSelf();
        ContractManagement.update(nef, manifest, data);
    }
    //endregion PROPOSAL-INVOKED METHODS

    public static void pause() {
        assert checkWitness(calcMembersMultiSigAccount()) : "GrantSharesGov: Not authorized";
        Storage.put(ctx, PAUSED_KEY, 1);
    }

    public static void unpause() {
        assert checkWitness(calcMembersMultiSigAccount()) : "GrantSharesGov: Not authorized";
        Storage.put(ctx, PAUSED_KEY, 0);
    }

    private static void assertCallerIsSelf() {
        assert Runtime.getCallingScriptHash() == Runtime.getExecutingScriptHash() :
                "GrantSharesGov: Method only callable by the contract itself";
    }

    public static void assertNotPaused() {
        assert !isPaused() : "GrantSharesGov: Contract is paused";
    }

}