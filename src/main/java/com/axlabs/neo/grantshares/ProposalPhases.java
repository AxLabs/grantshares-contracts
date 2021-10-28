package com.axlabs.neo.grantshares;

public class ProposalPhases {

    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;

    public ProposalPhases(int reviewEnd, int votingEnd, int queuedEnd) {
        this.reviewEnd = reviewEnd;
        this.votingEnd = votingEnd;
        this.queuedEnd = queuedEnd;
    }
}
