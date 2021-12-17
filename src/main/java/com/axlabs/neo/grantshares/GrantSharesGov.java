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
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event5Args;

import static io.neow3j.devpack.Helper.memcpy;
import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.constants.FindOptions.KeysOnly;
import static io.neow3j.devpack.constants.FindOptions.RemovePrefix;
import static io.neow3j.devpack.contracts.CryptoLib.sha256;
import static io.neow3j.devpack.contracts.LedgerContract.currentIndex;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
@SuppressWarnings("unchecked")
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
    static final String NR_OF_MEMBERS_KEY = "#_members";
    static final String NR_OF_PROPOSALS_KEY = "#_proposals";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap((byte) 1);
    static final StorageMap proposalsEnumerated = ctx.createMap((byte) 2);
    static final StorageMap proposalVotes = ctx.createMap((byte) 3);
    static final StorageMap parameters = ctx.createMap((byte) 5);
    static final byte membersMapPrefix = 6;
    // Maps member hashes to the block index at which they became a member.
    static final StorageMap members = ctx.createMap(membersMapPrefix);
    //endregion CONTRACT VARIABLES

    //region EVENTS
    @DisplayName("ProposalCreated")
    static Event5Args<ByteString, Hash160, String, Integer, Integer> created;
    @DisplayName("ProposalIntent")
    static Event2Args<ByteString, Intent> intent;
    @DisplayName("ProposalEndorsed")
    static Event2Args<ByteString, Hash160> endorsed;
    @DisplayName("Voted")
    static Event3Args<ByteString, Hash160, Integer> voted;
    @DisplayName("ProposalExecuted")
    static Event1Arg<ByteString> executed;
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
            Storage.put(ctx, NR_OF_MEMBERS_KEY, initialMembers.size());
            Storage.put(ctx, NR_OF_PROPOSALS_KEY, 0);
            List<Object> params = (List<Object>) membersAndParams.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                parameters.put((ByteString) params.get(i), (int) params.get(i + 1));
            }
        }
    }

    //region SAFE METHODS

    /**
     * Creates a hash for the given proposal intents and description hash. The hash is unique for
     * given input, i.e., it is very hard to find intents and descriptionHash that would lead to
     * a hash collision.
     *
     * @param intents         The proposal intents.
     * @param descriptionHash The hash of the proposal's description.
     * @return the hash.
     */
    @Safe
    public static ByteString hashProposal(Intent[] intents, ByteString descriptionHash) {
        ByteString b = new ByteString("");
        for (Intent i : intents) {
            // Concatenate target contract
            assert Hash160.isValid(i.targetContract)
                    : "GrantSharesGov: Invalid target contract hash";
            b = b.concat(i.targetContract.toByteString());

            // Pad and concatenate target method
            byte[] paddedMethod = new byte[MAX_METHOD_LEN];
            ByteString methodBytes = new ByteString(i.method);
            assert methodBytes.length() <= GrantSharesGov.MAX_METHOD_LEN
                    : "GrantSharesGov: Target method name too long";
            memcpy(paddedMethod, 0, methodBytes, 0, methodBytes.length());
            b = b.concat(paddedMethod);

            // Pad and concatenate method parameters
            for (Object p : i.params) {
                // TODO: Consider breaking compound stack items (e.g., list or map) down and
                //  serializing their parts with a smaller padded byte array size.
                byte[] paddedParam = new byte[MAX_SERIALIZED_INTENT_PARAM_LEN];
                ByteString paramBytes = serialize(p);
                assert paramBytes.length() <= MAX_SERIALIZED_INTENT_PARAM_LEN
                        : "GrantSharesGov: Intent method parameter too big";
                memcpy(paddedParam, 0, paramBytes, 0, paramBytes.length());
                b = b.concat(paddedParam);
            }
        }
        return sha256(b.concat(descriptionHash));
    }

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
     * Gets the proposal with {@code proposalHash}.
     *
     * @param proposalHash The proposal's hash.
     * @return the proposal.
     */
    @Safe
    public static Proposal getProposal(ByteString proposalHash) {
        ByteString bytes = proposals.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (Proposal) deserialize(proposals.get(proposalHash));
    }

    /**
     * Gets the votes of the proposal with {@code proposalHash}.
     *
     * @param proposalHash The proposal's hash.
     * @return the votes.
     */
    @Safe
    public static ProposalVotes getProposalVotes(ByteString proposalHash) {
        ByteString bytes = proposalVotes.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (ProposalVotes) deserialize(proposalVotes.get(proposalHash));
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
        return Storage.getInteger(ctx, NR_OF_PROPOSALS_KEY);
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
        int n = Storage.getInteger(ctx, NR_OF_PROPOSALS_KEY);
        return Paginator.paginate(n, page, itemsPerPage, proposalsEnumerated);
    }

    //endregion SAFE METHODS

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer    The account set as the proposer.
     * @param intents     The intents to be executed when the proposal is accepted.
     * @param description The proposal description. Must not be larger than 1024 bytes.
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents, String description,
            ByteString linkedProposal) {

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
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents,
            String description, ByteString linkedProposal, int acceptanceRate, int quorum) {

        assert checkWitness(proposer) : "GrantSharesGov: Not authorised";
        assert acceptanceRate >= parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesGov: Acceptance rate not allowed";
        assert quorum >= parameters.getInteger(MIN_QUORUM_KEY)
                : "GrantSharesGov: Quorum not allowed";
        assert linkedProposal == null || proposals.get(linkedProposal) != null
                : "GrantSharesGov: Linked proposal doesn't exist";
        assert description.length() <= 1024 : "GrantSharesGov: Description too long";

        ByteString proposalHash = hashProposal(intents, sha256(new ByteString(description)));
        ByteString proposalBytes = proposals.get(proposalHash.toByteArray());
        assert proposalBytes == null : "GrantSharesGov: Proposal already exists";

        proposals.put(proposalHash, serialize(new Proposal(proposalHash, proposer,
                linkedProposal, acceptanceRate, quorum)));
        int n = Storage.getInteger(ctx, NR_OF_PROPOSALS_KEY);
        proposalsEnumerated.put(n, proposalHash);
        Storage.put(ctx, NR_OF_PROPOSALS_KEY, n + 1);

        // An event cannot take a large amount of data, i.e., we should not pass the description,
        // since it might be too large. The max size of a state item is 1024 bytes.
        // TODO: Test if the description is actually allowed to be 1024 bytes long.
        created.fire(proposalHash, proposer, description, acceptanceRate, quorum);
        for (Intent i : intents) {
            intent.fire(proposalHash, i);
        }
        return proposalHash;
    }

    /**
     * If the {@code endorser} is a DAO member and the proposal is an existing un-endorsed
     * proposal, it becomes endorsed and its phases are set.
     *
     * @param proposalHash The proposal to endorse.
     * @param endorser     The script hash of the endorsing DAO member.
     */
    public static void endorseProposal(ByteString proposalHash, Hash160 endorser) {
        assert members.get(endorser.toByteString()) != null && checkWitness(endorser)
                : "GrantSharesGov: Not authorised";
        ByteString proposalBytes = proposals.get(proposalHash);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser == null : "GrantSharesGov: Proposal already endorsed";

        proposal.endorser = endorser;
        // Add +1 because the current idx is the block before this execution happens.
        proposal.reviewEnd = currentIndex() + 1 + parameters.getInteger(REVIEW_LENGTH_KEY);
        proposal.votingEnd = proposal.reviewEnd + parameters.getInteger(VOTING_LENGTH_KEY);
        proposal.queuedEnd = proposal.votingEnd + parameters.getInteger(QUEUED_LENGTH_KEY);
        proposals.put(proposalHash, serialize(proposal));
        proposalVotes.put(proposalHash, serialize(new ProposalVotes()));
        endorsed.fire(proposalHash, endorser);
    }

    /**
     * Casts a vote of the {@code voter} on the proposal with {@code proposalHash}.
     *
     * @param proposalHash The hash of the proposal to vote on.
     * @param vote         The vote. Must be either -1 for rejecting, 1 for approving or 0 for
     *                     abstaining.
     * @param voter        The script hash of the voter. Must be a member of the DAO and the
     *                     invoking script must hold a witness of the voter.
     * @throws Exception if the voter is not a DAO member, no witness for the voter is found, the
     *                   proposal does not exist or the proposal is not in its voting phase.
     */
    public static void vote(ByteString proposalHash, int vote, Hash160 voter) {
        assert vote >= -1 && vote <= 1 : "GrantSharesGov: Illegal vote";
        assert members.get(voter.toByteString()) != null && checkWitness(voter)
                : "GrantSharesGov: Not authorised";
        ByteString proposalBytes = proposals.get(proposalHash);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser != null : "GrantSharesGov: Proposal wasn't endorsed";
        int currentIdx = currentIndex();
        assert currentIdx >= proposal.reviewEnd && currentIdx < proposal.votingEnd
                : "GrantSharesGov: Proposal not active";

        // No need for null check. This map was created in the endorsement and we know the
        // endorsement happened because the proposal phases were set.
        ProposalVotes pv = (ProposalVotes) deserialize(proposalVotes.get(proposalHash));
        if (vote < 0) {
            pv.reject += 1;
        } else if (vote > 0) {
            pv.approve += 1;
        } else {
            pv.abstain += 1;
        }
        proposalVotes.put(proposalHash, serialize(pv));
        voted.fire(proposalHash, voter, vote);
    }

    /**
     * Executes the proposal with the given {@code intents} and {@code description}. Anyone
     * can execute a proposal.
     * <p>
     * Execution is only successful if the proposal is out of its queued phase, was accepted and
     * does not have a connected abrogation proposal that was accepted.
     *
     * @param intents     The intents of the proposal.
     * @param description The proposal's description.
     * @return the values returned the called intents.
     */
    public static Object[] execute(Intent[] intents, String description) {
        ByteString proposalHash = hashProposal(intents, sha256(new ByteString(description)));
        ByteString proposalBytes = proposals.get(proposalHash);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        Proposal proposal = (Proposal) deserialize(proposalBytes);
        assert proposal.endorser != null : "GrantSharesGov: Proposal wasn't endorsed yet";
        ByteString proposalVotesBytes = proposalVotes.get(proposalHash);
        assert currentIndex() >= proposal.queuedEnd
                : "GrantSharesGov: Proposal not in execution phase";

        ProposalVotes votes = (ProposalVotes) deserialize(proposalVotesBytes);
        int voteCount = votes.approve + votes.abstain + votes.reject;
        assert voteCount * 100 / Storage.getInteger(ctx, NR_OF_MEMBERS_KEY) >= proposal.quorum
                : "GrantSharesGov: Quorum not reached";
        assert votes.approve * 100 / voteCount >= proposal.acceptanceRate
                : "GrantSharesGov: Proposal rejected";
        // TODO: Check if has abrogation proposal that was accepted.
        assert !proposal.executed : "GrantSharesGov: Proposal already executed";
        proposal.executed = true;
        proposals.put(proposalHash, serialize(proposal));

        Object[] returnVals = new Object[intents.length];
        for (int i = 0; i < intents.length; i++) {
            Intent t = intents[i];
            returnVals[i] = Contract.call(t.targetContract, t.method, CallFlags.All, t.params);
        }
        executed.fire(proposalHash);
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