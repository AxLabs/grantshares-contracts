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
    static Event6Args<Hash256, Hash160, Intent[], String, Integer, Integer> created;
    @DisplayName("ProposalEndorsed")
    static Event5Args<Hash256, Hash160, Integer, Integer, Integer> endorsed;

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            List<Object> params = (List<Object>) data;
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
    public static Hash256 hashProposal(Intent[] intents, ByteString descriptionHash) {
        ByteString b = new ByteString("");
        for (Intent i : intents) {
            b.concat(i.targetContract.toByteString());
            b.concat(i.method);
            for (Object p : i.params) {
                b.concat(serialize(p));
            }
        }
        return new Hash256(sha256(b.concat(descriptionHash)));
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
    public static Hash256 createProposal(Hash160 proposer, Intent[] intents, String description) {
        return createProposal(
                proposer,
                intents,
                description,
                null,
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
    public static Hash256 createProposal(Hash160 proposer, Intent[] intents, String description,
            Hash256 linkedProposal, int acceptanceRate, int quorum) {

        assert checkWitness(proposer) : "GrantSharesDAO: Proposer not authorised";
        assert acceptanceRate >= parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesDAO: Acceptance rate not allowed";
        assert quorum >= parameters.getInteger(MIN_QUORUM_KEY)
                : "GrantSharesDAO: Quorum not allowed";

        Hash256 proposalHash = hashProposal(intents, sha256(new ByteString(description)));
        ByteString proposalBytes = proposals.get(proposalHash.toByteArray());
        assert proposalBytes == null : "GrantSharesDAO: Proposal already exists";

        proposals.put(proposalHash.toByteString(), serialize(new Proposal(proposalHash, proposer,
                linkedProposal, acceptanceRate, quorum)));

        created.fire(proposalHash, proposer, intents, description, acceptanceRate, quorum);

        return proposalHash;
    }

    public static void endorseProposal(Hash256 proposalHash, Hash160 endorser) {
        assert members.get(endorser.toByteString()) != null && checkWitness(endorser)
                : "GrantSharesDAO: No authorisation";
        assert proposals.get(proposalHash.toByteString()) != null
                : "GrantSharesDAO: Proposal doesn't exist";

        // Add +1 because the current idx is the block before this execution happens.
        int reviewEnd = currentIndex() + 1 + parameters.getInteger(REVIEW_LENGTH_KEY);
        int votingEnd = reviewEnd + parameters.getInteger(VOTING_LENGTH_KEY);
        int queuedEnd = votingEnd + parameters.getInteger(QUEUED_LENGTH_KEY);
        proposalPhases.put(proposalHash.toByteString(),
                serialize(new ProposalPhases(reviewEnd, votingEnd, queuedEnd)));

        proposalVotes.put(proposalHash.toByteString(), serialize(new ProposalVotes(1, 0, 0)));

        endorsed.fire(proposalHash, endorser, reviewEnd, votingEnd, queuedEnd);
    }

    public static Proposal getProposal(Hash256 proposalHash) {
        return (Proposal) deserialize(proposals.get(proposalHash.toByteString()));
    }

    public static ProposalVotes getProposalVotes(Hash256 proposalHash) {
        return (ProposalVotes) deserialize(proposalVotes.get(proposalHash.toByteString()));
    }

    public static ProposalPhases getProposalPhases(Hash256 proposalHash) {
        return (ProposalPhases) deserialize(proposalPhases.get(proposalHash.toByteString()));
    }

    public static Object execute(Intent[] intents, ByteString descriptionHash) {
        Hash256 proposalHash = hashProposal(intents, descriptionHash);
        ByteString proposalBytes = proposals.get(proposalHash.toByteString());
        assert proposalBytes != null : "GrantSharesDAO: Proposal doesn't exist";
        Proposal p = (Proposal) deserialize(proposalBytes);

        Object returnVal = Contract.call(intents[0].targetContract, intents[0].method,
                CallFlags.All, intents[0].params);

        return returnVal;
    }

    public static void vote(Hash256 proposalHash, int vote, Hash160 member) {
        assert members.get(member.toByteString()) != null && checkWitness(member)
                : "GrantSharesDAO: Not a member";
        ByteString ppBytes = proposalPhases.get(proposalHash.toByteString());
        assert ppBytes != null : "GrantSharesDAO: Proposal doesn't exist or wasn't endorsed yet.";
        ProposalPhases pp = (ProposalPhases) deserialize(ppBytes);
        int currentIdx = currentIndex();
        assert currentIdx > pp.reviewEnd && currentIdx <= pp.votingEnd
                : "GrantSharesDAO: Proposal not active.";
    }

}