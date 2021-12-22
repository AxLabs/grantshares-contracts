package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;

public class ProposalData {

    public Hash160 proposer;
    public int linkedProposal;
    public int acceptanceRate;
    public int quorum;
    public Intent[] intents;
    public String desc;

    public ProposalData(Hash160 proposer, int linkedProposal, int acceptanceRate,
            int quorum, Intent[] intents, String desc) {
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
        this.intents = intents;
        this.desc = desc;
    }
}
