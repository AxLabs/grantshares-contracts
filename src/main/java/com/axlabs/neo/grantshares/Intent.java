package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Hash160;

/**
 * Represents an action of a proposal. Proposals are made up of one or more intents that are executed once the
 * proposal is accepted.
 */
public class Intent {

    /**
     * The contract to be called.
     */
    public Hash160 targetContract;

    /**
     * The method to be called.
     */
    public String method;

    /**
     * The parameters to pass to the method.
     */
    public Object[] params;

    public Intent(Hash160 targetContract, String method, Object[] params) {
        this.targetContract = targetContract;
        this.method = method;
        this.params = params;
    }
}
