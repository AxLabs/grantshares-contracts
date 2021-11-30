package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Hash256;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event5Args;

import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.contracts.CryptoLib.sha256;
import static io.neow3j.devpack.contracts.LedgerContract.currentIndex;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
@SuppressWarnings("unchecked")
public class GrantSharesGov { //TODO: test with extends

    // Storage, Keys, Prefixes
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String QUEUED_LENGTH_KEY = "queued_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MAX_FUNDING_AMOUNT_KEY = "max_funding";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap(1);
    static final StorageMap proposalVotes = ctx.createMap(2);
    static final StorageMap proposalPhases = ctx.createMap(3);
    static final StorageMap parameters = ctx.createMap(4);
    static final StorageMap members = ctx.createMap(5);

    // Events
    @DisplayName("ProposalCreated")
    static Event5Args<ByteString, Hash160, ByteString, Integer, Integer> created;
    @DisplayName("ProposalIntent")
    static Event2Args<ByteString, Intent> intent;
    @DisplayName("ProposalEndorsed")
    static Event5Args<ByteString, Hash160, Integer, Integer, Integer> endorsed;
    @DisplayName("Voted")
    static Event3Args<ByteString, Hash160, Integer> voted;

    @OnDeployment
    @SuppressWarnings("unchecked")
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            List<Object> membersAndParams = (List<Object>) data;
            List<Hash160> initialMembers = (List<Hash160>) membersAndParams.get(0);
            int blockIdx = currentIndex();
            for (int i = 0; i < initialMembers.size(); i++) {
                // TODO: Any information we should map the members to?
                members.put(initialMembers.get(i).toByteString(), blockIdx);
            }
            List<Object> params = (List<Object>) membersAndParams.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                parameters.put((ByteString) params.get(i), (int) params.get(i + 1));
            }
        }
    }

    // TODO: Is it possible to create collisions with different inputs?
    //  In the open-zeppelin implementation the method abi.encode is called on the intents. This
    //  encodes each value in the intents with 256-bit. Therefore, it is hard to find another
    //  proposal with the same hash. But in our implementation, every value takes exactly the
    //  amount of space that it needs. Creating a proposal with different intents but with the
    //  same hash is easier.
    @Safe
    public static ByteString hashProposal(Intent[] intents, ByteString descriptionHash) {
        ByteString b = new ByteString("");
        for (Intent i : intents) {
            b.concat(i.targetContract.toByteString());
            b.concat(i.method);
            for (Object p : i.params) {
                b.concat(serialize(p));
            }
        }
        return sha256(b.concat(descriptionHash));
    }

    @Safe
    public static Object getParameter(String paramName) {
        return parameters.get(paramName);
    }

    @Safe
    public static Proposal getProposal(ByteString proposalHash) {
        ByteString bytes = proposals.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (Proposal) deserialize(proposals.get(proposalHash));
    }

    @Safe
    public static Map<Hash160, Integer> getProposalVotes(ByteString proposalHash) {
        ByteString bytes = proposalVotes.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (Map<Hash160, Integer>) deserialize(proposalVotes.get(proposalHash));
    }

    @Safe
    public static ProposalPhases getProposalPhases(ByteString proposalHash) {
        ByteString bytes = proposalPhases.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (ProposalPhases) deserialize(proposalPhases.get(proposalHash));
    }

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer    The account set as the proposer.
     * @param intents     The intents to be executed when the proposal is accepted.
     * @param description A description of the proposals intents.
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents,
            String description, ByteString linkedProposal) {

        return createProposal(
                proposer,
                intents,
                description,
                linkedProposal,
                parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY),
                parameters.getInteger(MIN_QUORUM_KEY));
    }

    /**
     * Creates a proposal.
     *
     * @param proposer       The account set as the proposer.
     * @param intents        The intents to be executed when the proposal is accepted.
     * @param description    A description of the proposals intents.
     * @param linkedProposal A proposal that preceded this one.
     * @param acceptanceRate The desired acceptance rate.
     * @param quorum         The desired quorum.
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents, String description,
            ByteString linkedProposal, int acceptanceRate, int quorum) {

        assert checkWitness(proposer) : "GrantSharesDAO: Not authorised";
        assert acceptanceRate >= parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesDAO: Acceptance rate not allowed";
        assert quorum >= parameters.getInteger(MIN_QUORUM_KEY)
                : "GrantSharesDAO: Quorum not allowed";
        assert linkedProposal == null || proposals.get(linkedProposal) != null
                : "GrantSharesDAO: Linked proposal doesn't exist";

        // We don't perform any validation of the intents. It is up to the endorser and other DAO
        // members to check if they are functional.
        ByteString descriptionHash = sha256(new ByteString(description));
        ByteString proposalHash = hashProposal(intents, descriptionHash);
        ByteString proposalBytes = proposals.get(proposalHash.toByteArray());
        assert proposalBytes == null : "GrantSharesDAO: Proposal already exists";

        proposals.put(proposalHash, serialize(new Proposal(proposalHash, proposer,
                linkedProposal, acceptanceRate, quorum)));

        // An event cannot take a large amount of data, i.e., we should not pass the description,
        // since it might be too large. The max size of a state item is 1024 bytes.
        created.fire(proposalHash, proposer, descriptionHash, acceptanceRate, quorum);
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
     * @param endorser The script hash of the endorsing DAO member.
     */
    public static void endorseProposal(ByteString proposalHash, Hash160 endorser) {
        assert members.get(endorser.toByteString()) != null && checkWitness(endorser)
                : "GrantSharesDAO: Not authorised";
        assert proposals.get(proposalHash) != null
                : "GrantSharesDAO: Proposal doesn't exist";
        assert proposalPhases.get(proposalHash) == null
                : "GrantSharesDAO: Proposal already endorsed";

        // Add +1 because the current idx is the block before this execution happens.
        int reviewEnd = currentIndex() + 1 + parameters.getInteger(REVIEW_LENGTH_KEY);
        int votingEnd = reviewEnd + parameters.getInteger(VOTING_LENGTH_KEY);
        int queuedEnd = votingEnd + parameters.getInteger(QUEUED_LENGTH_KEY);
        proposalPhases.put(proposalHash, serialize(
                new ProposalPhases(reviewEnd, votingEnd, queuedEnd)));

        Map<Hash160, Integer> votes = new Map<>();
        votes.put(endorser, 1);
        proposalVotes.put(proposalHash, serialize(votes));

        endorsed.fire(proposalHash, endorser, reviewEnd, votingEnd, queuedEnd);
    }

    public static Object execute(Intent[] intents, ByteString descriptionHash) {
        ByteString proposalHash = hashProposal(intents, descriptionHash);
        ByteString proposalBytes = proposals.get(proposalHash);
        assert proposalBytes != null : "GrantSharesDAO: Proposal doesn't exist";
        Proposal p = (Proposal) deserialize(proposalBytes);

        Object returnVal = Contract.call(intents[0].targetContract, intents[0].method,
                CallFlags.All, intents[0].params);

        return returnVal;
    }

    /**
     * Casts a vote of the {@code voter} on the proposal with {@code proposalHash}.
     *
     * @param proposalHash The hash of the proposal to vote on.
     * @param vote         The vote. Must be either -1 for rejecting, 1 for approving or 0 for
     *                     abstaining.
     * @param voter        The script hash of the voter. Must be a member of the DAO and the
     *                     invoking
     *                     script must hold a witness of the voter.
     * @throws Exception if the voter is not a DAO member, no witness for the voter is found, the
     *                   proposal does not exist or the proposal is not in its voting phase.
     */
    public static void vote(ByteString proposalHash, int vote, Hash160 voter) {
        assert vote >= -1 && vote <= 1 : "GrantSharesDAO: Illegal vote. Right to jail!";
        assert members.get(voter.toByteString()) != null && checkWitness(voter)
                : "GrantSharesDAO: Not authorised";
        ByteString ppBytes = proposalPhases.get(proposalHash);
        assert ppBytes != null : "GrantSharesDAO: Proposal doesn't exist or wasn't endorsed yet.";
        ProposalPhases pp = (ProposalPhases) deserialize(ppBytes);
        int currentIdx = currentIndex();
        assert currentIdx >= pp.reviewEnd && currentIdx < pp.votingEnd
                : "GrantSharesDAO: Proposal not active.";

        // No need for null check. This map was created in the endorsement.
        Map<Hash160, Integer> pv = (Map<Hash160, Integer>) deserialize(
                proposalVotes.get(proposalHash));
        pv.put(voter, vote);
        proposalVotes.put(proposalHash, serialize(pv));

        voted.fire(proposalHash, voter, vote);
    }

}