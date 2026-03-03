package com.axlabs.neo.grantshares.util.proposal;

public class ProposalData {

    private String offchainUri;
    private int linkedProposal;

    public ProposalData(String offchainUri, int linkedProposal) {
        this.offchainUri = offchainUri;
        this.linkedProposal = linkedProposal;
    }

    public int getLinkedProposal() {
        return linkedProposal;
    }

    public String getOffChainUri() {
        return offchainUri;
    }

}
