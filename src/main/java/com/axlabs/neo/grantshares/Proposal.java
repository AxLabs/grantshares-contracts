package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;

public class Proposal {

    public ByteString hash;
    public Hash160 proposer;
    public ByteString linkedProposal;

    public int acceptanceRate;
    public int quorum;

    public Hash160 endorser;
    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;

    public boolean executed;

    public Proposal(ByteString hash, Hash160 proposer, ByteString linkedProposal,
            int acceptanceRate, int quorum) {

        this.hash = hash;
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
        endorser = null;
        reviewEnd = 0;
        votingEnd = 0;
        queuedEnd = 0;
        executed = false;
    }
}
