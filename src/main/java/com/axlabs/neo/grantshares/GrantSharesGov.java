package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;
import io.neow3j.devpack.events.Event5Args;

import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.constants.FindOptions.KeysOnly;
import static io.neow3j.devpack.constants.FindOptions.RemovePrefix;
import static io.neow3j.devpack.contracts.LedgerContract.currentIndex;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@Permission(contract = "*", methods = "*")
@SuppressWarnings("unchecked")
@DisplayName("ConcealedContractV1")
public class GrantSharesGov {

    //region CONTRACT VARIABLES
    // Constants
    static final int MAX_METHOD_LEN = 128;
    static final int MAX_SERIALIZED_INTENT_PARAM_LEN = 1024;

    // Storage, Keys, Prefixes
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String QUEUED_LENGTH_KEY = "queued_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MAX_FUNDING_AMOUNT_KEY = "max_funding";
    static final String MEMBERS_COUNT_KEY = "#_members";
    static final String PROPOSALS_COUNT_KEY = "#_proposals";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap((byte) 1);
    static final StorageMap proposalData = ctx.createMap((byte) 2);
    static final StorageMap proposalVotes = ctx.createMap((byte) 3);
    static final StorageMap parameters = ctx.createMap((byte) 5);
    static final byte membersMapPrefix = 6;
    // Maps member hashes to the block index at which they became a member.
    static final StorageMap members = ctx.createMap(membersMapPrefix);
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
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            List<Object> membersAndParams = (List<Object>) data;
            List<Hash160> initialMembers = (List<Hash160>) membersAndParams.get(0);
            int blockIdx = currentIndex();
            for (int i = 0; i < initialMembers.size(); i++) {
                members.put(initialMembers.get(i).toByteString(), blockIdx);
            }
            Storage.put(ctx, MEMBERS_COUNT_KEY, initialMembers.size());
            Storage.put(ctx, PROPOSALS_COUNT_KEY, 0);
            List<Object> params = (List<Object>) membersAndParams.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                parameters.put((ByteString) params.get(i), (int) params.get(i + 1));
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
            dto.desc = p.desc;
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
     * Returns the hashes of the governance members.
     *
     * @return the members.
     */
    @Safe
    public static List<Hash160> getMembers() {
        Iterator<ByteString> it = Storage.find(ctx, membersMapPrefix,
                (byte) (KeysOnly | RemovePrefix));
        List<Hash160> members = new List<>();
        while (it.next()) {
            members.add(new Hash160(it.get()));
        }
        return members;
    }

    /**
     * Gets the number of proposals created on this contract.
     *
     * @return the number of proposals.
     */
    @Safe
    public static int getProposalCount() {
        return Storage.getInteger(ctx, PROPOSALS_COUNT_KEY);
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
        int n = Storage.getInteger(ctx, PROPOSALS_COUNT_KEY);
        int[] pagination = Paginator.calcPagination(n, page, itemsPerPage);
        List<Object> list = new List<>();
        for (int i = pagination[0]; i < pagination[1]; i++) {
            list.add(getProposal(i));
        }
        return new Paginator.Paginated(page, pagination[2], list);
    }

    //endregion SAFE METHODS

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer    The account set as the proposer.
     * @param intents     The intents to be executed when the proposal is accepted.
     * @param description The proposal description. Must not be larger than 1024 bytes.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents, String description,
            int linkedProposal) {

        return createProposal(proposer, intents, description, linkedProposal,
                parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY),
                parameters.getInteger(MIN_QUORUM_KEY));
    }

    /**
     * Creates a proposal.
     *
     * @param proposer       The account set as the proposer.
     * @param intents        The intents to be executed when the proposal is accepted.
     * @param description    The proposal description. Must not be larger than 1024 bytes.
     * @param linkedProposal A proposal that preceded this one.
     * @param acceptanceRate The desired acceptance rate.
     * @param quorum         The desired quorum.
     * @return The id of the proposal.
     */
    public static int createProposal(Hash160 proposer, Intent[] intents,
            String description, int linkedProposal, int acceptanceRate, int quorum) {

        // TODO: Check for targetContract validity and if they target only the Governance and
        //  Treasury contracts.
        assert checkWitness(proposer) : "GrantSharesGov: Not authorised";
        assert acceptanceRate >= parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesGov: Acceptance rate not allowed";
        assert quorum >= parameters.getInteger(MIN_QUORUM_KEY)
                : "GrantSharesGov: Quorum not allowed";
        assert linkedProposal < 0 || proposals.get(linkedProposal) != null
                : "GrantSharesGov: Linked proposal doesn't exist";

        int id = Storage.getInteger(ctx, PROPOSALS_COUNT_KEY);
        proposals.put(id, serialize(new Proposal(id)));
        proposalData.put(id, serialize(new ProposalData(proposer, linkedProposal, acceptanceRate,
                quorum, intents, description)));
        proposalVotes.put(id, serialize(new ProposalVotes()));
        Storage.put(ctx, PROPOSALS_COUNT_KEY, id + 1);

        // An event can take max 1024 bytes state data. Thus, we're not passing the description.
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
        // Add +1 because the current idx is the block before this execution happens.
        proposal.reviewEnd = currentIndex() + 1 + parameters.getInteger(REVIEW_LENGTH_KEY);
        proposal.votingEnd = proposal.reviewEnd + parameters.getInteger(VOTING_LENGTH_KEY);
        proposal.queuedEnd = proposal.votingEnd + parameters.getInteger(QUEUED_LENGTH_KEY);
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
        int currentIdx = currentIndex();
        assert currentIdx >= proposal.reviewEnd && currentIdx < proposal.votingEnd
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
        assert currentIndex() >= proposal.queuedEnd
                : "GrantSharesGov: Proposal not in execution phase";
        assert !proposal.executed : "GrantSharesGov: Proposal already executed";
        ProposalData data = (ProposalData) deserialize(proposalData.get(id));
        ProposalVotes votes = (ProposalVotes) deserialize(proposalVotes.get(id));
        int voteCount = votes.approve + votes.abstain + votes.reject;
        assert voteCount * 100 / Storage.getInteger(ctx, MEMBERS_COUNT_KEY) >= data.quorum
                : "GrantSharesGov: Quorum not reached";
        assert votes.approve * 100 / voteCount >= data.acceptanceRate
                : "GrantSharesGov: Proposal rejected";

        // TODO: Check if has abrogation proposal that was accepted.

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

    //region PROPOSAL-INVOKED METHODS
    public static void changeParam(String paramKey, Object value) {
        assertCallerIsSelf();
        parameters.put(paramKey, (byte[]) value);
        paramChanged.fire(paramKey, (byte[]) value);
    }

    public static void addMember(Hash160 member) {
        assertCallerIsSelf();
        assert Hash160.isValid(member) : "GrantSharesGov: Not a valid account hash";
        ByteString blockIndexBytes = members.get(member.toByteString());
        assert blockIndexBytes == null : "GrantSharesGov: Already a member";
        members.put(member.toByteString(), LedgerContract.currentIndex());
        memberAdded.fire(member);
    }

    public static void removeMember(Hash160 member) {
        assertCallerIsSelf();
        assert Hash160.isValid(member) : "GrantSharesGov: Not a valid account hash";
        ByteString blockIndexBytes = members.get(member.toByteString());
        assert blockIndexBytes != null : "GrantSharesGov: Not a member";
        members.delete(member.toByteString());
        memberRemoved.fire(member);
    }

    public static void updateContract(ByteString nef, String manifest, Object data) {
        assertCallerIsSelf();
        ContractManagement.update(nef, manifest, data);
    }
    //endregion PROPOSAL-INVOKED METHODS

    private static void assertCallerIsSelf() {
        assert Runtime.getCallingScriptHash() == Runtime.getExecutingScriptHash() :
                "GrantSharesGov: Method only callable by the contract itself";
    }

}