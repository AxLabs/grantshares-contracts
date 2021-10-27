package com.axlabs.neo.grantshares;

public class ProposalVotes {

    public int approvals;
    public int refusals;
    public int abstains;

    public ProposalVotes(int approvals, int refusals, int abstains) {
        this.approvals = approvals;
        this.refusals = refusals;
        this.abstains = abstains;
    }
}

