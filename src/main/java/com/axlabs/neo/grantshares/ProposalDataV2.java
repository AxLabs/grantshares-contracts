package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

/**
 * Proposal information that is set at the time of creation of a proposal and doesn't change after that.
 * This data was separated from {@link Proposal} in order to save storage costs when updating a proposal.
 */
public class ProposalDataV2 {

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

    /**
     * The actual number of votes required for this proposal to reach quorum.
     * This is calculated at proposal creation time based on the quorum percentage
     * and total number of members at that time.
     */
    public int quorumVotes;

    public ProposalDataV2(Hash160 proposer, int linkedProposal, int acceptanceRate,
            int quorum, Intent[] intents, String offchainUri) {
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
        this.intents = intents;
        this.offchainUri = offchainUri;
        quorumVotes = 0;
    }

    public ProposalDataV2(ProposalDataV1 oldProposalData, int memberCount) {
        this.proposer = oldProposalData.proposer;
        this.linkedProposal = oldProposalData.linkedProposal;
        this.acceptanceRate = oldProposalData.acceptanceRate;
        this.quorum = oldProposalData.quorum;
        this.intents = oldProposalData.intents;
        this.offchainUri = oldProposalData.offchainUri;
        computeQuorumVotes(memberCount);
    }

    public void computeQuorumVotes(int memberCount) {
        if (quorumVotes == 0) {
            quorumVotes = (memberCount * quorum) / 100;
            if ((memberCount * quorum) % 100 != 0) {
                quorumVotes += 1; // Round up
            }
        }
    }
}

