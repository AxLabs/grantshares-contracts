package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

public class Proposal {

    public int id;
    public Hash160 proposer;
    int yesVotes;
    int noVotes;

    public Hash160 contract;
    public String method;
    public Object[] parameters;

}
