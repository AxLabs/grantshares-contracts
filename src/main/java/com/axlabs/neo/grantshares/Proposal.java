package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Hash256;

public class Proposal {

    public Hash256 hash;
    public Hash160 proposer;
    public Hash256 linkedProposal;

    // Indices of period endings
    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;

    // Approval conditions
    public int acceptanceRate;
    public int quorum;

    public Proposal(Hash256 hash, Hash160 proposer, Hash256 linkedProposal, int reviewEnd,
            int votingEnd, int queuedEnd, int acceptanceRate, int quorum) {

        this.hash = hash;
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.reviewEnd = reviewEnd;
        this.votingEnd = votingEnd;
        this.queuedEnd = queuedEnd;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
    }
}
