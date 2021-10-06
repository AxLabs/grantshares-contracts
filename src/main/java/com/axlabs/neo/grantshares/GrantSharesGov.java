package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.StdLib;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
public class GrantSharesGov {

    static final byte[] PROPS_PREFIX = new byte[]{1};
    static final byte[] PROPS_COUNTER = new byte[]{2};

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap(PROPS_PREFIX);

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            Storage.put(ctx, PROPS_COUNTER, 0);
        }
    }

    public static int addProposal(Proposal proposal) throws Exception {
        int propId = Storage.getInteger(ctx, PROPS_COUNTER) + 1;
        proposal.id = propId;
        Storage.put(ctx, PROPS_COUNTER, propId);
        proposals.put(Helper.toByteArray(proposal.id), StdLib.serialize(proposal));
        return propId;
    }

    public static Proposal getProposal(int proposalId) {
        return (Proposal) StdLib.deserialize(proposals.get(Helper.toByteArray(proposalId)));
    }

    public static boolean execute(int proposalId) {
        Proposal p = (Proposal) StdLib.deserialize(proposals.get(Helper.toByteArray(proposalId)));
        Object result = Contract.call(Runtime.getExecutingScriptHash(), p.method, CallFlags.All,
                new Object[]{});
        return (boolean) result;
    }

    public static boolean callback() throws Exception {
        // Only this governance contract itself should be able to call this method.
        if (Runtime.getCallingScriptHash() != Runtime.getExecutingScriptHash()) {
            throw new Exception("No authorization.");
        }
        return true;
    }

}