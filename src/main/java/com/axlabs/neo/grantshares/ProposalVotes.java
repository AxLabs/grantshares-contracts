package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Map;

/**
 * The struct holding the number of yes, no, and abstain votes for a proposal.
 */
public class ProposalVotes {

    /**
     * The number of votes approving the proposal.
     */
    public int approve;

    /**
     * The number of votes rejecting the proposal.
     */
    public int reject;

    /**
     * The number of votes that abstain from yes or no position.
     */
    public int abstain;

    /**
     * Holds information about what members voted on a porposal.
     */
    public Map<Hash160, Integer> voters;

    public ProposalVotes() {
        approve = 0;
        reject = 0;
        abstain = 0;
        voters = new Map<>();
    }
}
