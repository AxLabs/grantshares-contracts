package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Map;

public class ProposalVotes {

    public int approve;
    public int reject;
    public int abstain;

    public Map<Hash160, Integer> votes;

    public ProposalVotes() {
        approve = 0;
        reject = 0;
        abstain = 0;
        votes = new Map<>();
    }

}
