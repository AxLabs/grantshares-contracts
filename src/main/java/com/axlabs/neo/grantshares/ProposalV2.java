package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

/**
 * The base struct of a proposal created and stored when a user creates a proposal.
 * <p>
 * Additional proposal information is added via the {@link ProposalData struct}.
 */
public class ProposalV2 {

    /**
     * The proposals ID. IDs are assigned incrementally.
     */
    public int id;

    /**
     * The endorser of the proposal. Is set to null as long as the proposal is not endorsed.
     */
    public Hash160 endorser;

    /**
     * The actual number of votes required for this proposal to reach quorum.
     * <p>
     * This is calculated at proposal creation time based on the quorum percentage and total number of members at
     * that time.
     */
    public int quorumVotes;

    /**
     * The end of the review phase. Is set to zero as long as the proposal is not endorsed.
     */
    public int reviewEnd;

    /**
     * The end of the voting phase. Is set to zero as long as the proposal is not endorsed.
     */
    public int votingEnd;

    /**
     * The end of the time lock phase. Is set to zero as long as the proposal is not endorsed.
     */
    public int timeLockEnd;

    /**
     * The time at which this proposal expires. Expiration can happen before a proposal is endorsed or after a proposal
     * was accepted but not yet executed.
     */
    public int expiration;

    /**
     * Tells if this proposal was already executed.
     */
    public boolean executed;

    public ProposalV2(int id, int expiration) {
        this.id = id;
        endorser = null;
        this.quorumVotes = 0; // Initialize to 0, will be set once endorsed.
        reviewEnd = 0;
        votingEnd = 0;
        timeLockEnd = 0;
        this.expiration = expiration;
        executed = false;
    }

    public ProposalV2(ProposalV1 proposalV1) {
        this.id = proposalV1.id;
        this.endorser = proposalV1.endorser;
        this.quorumVotes = 0; // Initialize to 0, will be set once endorsed.
        this.reviewEnd = proposalV1.reviewEnd;
        this.votingEnd = proposalV1.votingEnd;
        this.timeLockEnd = proposalV1.timeLockEnd;
        this.expiration = proposalV1.expiration;
        this.executed = proposalV1.executed;
    }

    public void calculateAndSetQuorumVotes(int quorum, int memberCount) {
        if (quorumVotes == 0) {
            quorumVotes = (memberCount * quorum) / 100;
            if ((memberCount * quorum) % 100 != 0) {
                quorumVotes += 1; // Round up
            }
        }
    }
}
