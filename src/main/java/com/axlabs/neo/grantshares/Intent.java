package com.axlabs.neo.grantshares;


import io.neow3j.devpack.Hash160;

public class Intent {

    public Hash160 targetContract;
    public String method;
    public Object[] params;

    public Intent(Hash160 targetContract, String method, Object[] params) {
        this.targetContract = targetContract;
        this.method = method;
        this.params = params;
    }
}
