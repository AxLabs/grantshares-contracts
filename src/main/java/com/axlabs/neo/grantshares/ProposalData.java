package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

/**
 * Proposal information that is set at the time of creation of a proposal and doesn't change after that.
 * This data was separated from {@link Proposal} in order to save storage costs when updating a proposal.
 */
public class ProposalData {

    /**
     * The creator of the proposal.
     */
    public Hash160 proposer;

    /**
     * Allows linking to a corresponding proposal. Should be set to -1 if no linked proposal exists.
     */
    public int linkedProposal;

    /**
     * The necessary proportion of yes votes required for accepting this proposal. Given in percentage. E.g., a value
     * of 50 means that the simple majority is necessary for the proposal to pass.
     */
    public int acceptanceRate;

    /**
     * The necessary voter participation for this proposal to reach quorum. E.g., a value of 50 means that 50% of the
     * members have to vote in order for the proposal to reach its quorum.
     */
    public int quorum;

    /**
     * The proposal's intents executed if it gets accepted.
     */
    public Intent[] intents;

    /**
     * The URL of the GitHub issue where this proposal is discussed.
     */
    public String offchainUri;

    public ProposalData(Hash160 proposer, int linkedProposal, int acceptanceRate,
            int quorum, Intent[] intents, String offchainUri) {
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
        this.intents = intents;
        this.offchainUri = offchainUri;
    }
}
