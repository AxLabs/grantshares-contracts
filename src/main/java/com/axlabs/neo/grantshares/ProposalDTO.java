package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Map;

/**
 * Used to return all proposal information as one structure in getter methods.
 */
public class ProposalDTO {

    public int id;
    public Hash160 proposer;
    public int linkedProposal;
    public int acceptanceRate;
    public int quorum;
    public Hash160 endorser;
    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;
    public boolean executed;
    public Intent[] intents;
    public String desc;
    public int approve;
    public int reject;
    public int abstain;
    public Map<Hash160, Integer> voters;

    public ProposalDTO() {
    }
}
