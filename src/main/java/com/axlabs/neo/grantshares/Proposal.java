package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

/**
 * The base struct of a proposal created and stored when a user creates a proposal.
 * <p>
 * Additional proposal information is added via the {@link ProposalData struct}.
 */
public class Proposal {

    /**
     * The proposals ID. IDs are assigned incrementally.
     */
    public int id;

    /**
     * The endorser of the proposal. Is set to null as long as the proposal is not endorsed.
     */
    public Hash160 endorser;

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

    public Proposal(int id, int expiration) {
        this.id = id;
        endorser = null;
        reviewEnd = 0;
        votingEnd = 0;
        timeLockEnd = 0;
        this.expiration = expiration;
        executed = false;
    }
}
