package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

public class Proposal {

    public int id;
    public Hash160 endorser;
    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;
    public boolean executed;

    public Proposal(int id) {
        this.id = id;
        endorser = null;
        reviewEnd = 0;
        votingEnd = 0;
        queuedEnd = 0;
        executed = false;
    }
}
