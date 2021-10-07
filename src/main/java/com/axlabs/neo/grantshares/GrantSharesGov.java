package com.axlabs.neo.grantshares;

import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.StdLib;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
public class GrantSharesGov { //TODO: test with extends

    // Storage keys & prefixes
    static final byte[] PROPOSALS_PREFIX = new byte[]{1};
    static final byte[] PROPOSALS_COUNTER_KEY = new byte[]{2};
    static final byte[] PARAMETERS_PREFIX = new byte[]{3};

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap(PROPOSALS_PREFIX);
    static final StorageMap parameters = ctx.createMap(PARAMETERS_PREFIX);

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            Storage.put(ctx, PROPOSALS_COUNTER_KEY, 0);
            List<Object> params = (List<Object>) data;
            for (int i = 0; i < params.size(); i+=2) {
                parameters.put((String) params.get(i), (byte[]) params.get(i+1));
            }
        }
    }

    public static int addProposal(Proposal proposal) throws Exception {
        int propId = Storage.getInteger(ctx, PROPOSALS_COUNTER_KEY) + 1;
        proposal.id = propId;
        Storage.put(ctx, PROPOSALS_COUNTER_KEY, propId);
        proposals.put(Helper.toByteArray(proposal.id), StdLib.serialize(proposal));
        return propId;
    }

    @Safe
    public static Proposal getProposal(int proposalId) {
        return (Proposal) StdLib.deserialize(proposals.get(Helper.toByteArray(proposalId)));
    }

    public static Object execute(int proposalId) {
        Proposal p = (Proposal) StdLib.deserialize(proposals.get(Helper.toByteArray(proposalId)));
        Object result = Contract.call(Runtime.getExecutingScriptHash(),
                p.method, CallFlags.All, p.parameters);

        return result;
    }

    public static boolean callback(Hash160 param1, int param2) throws Exception {
        // Only this governance contract itself should be able to call this method.
        if (Runtime.getCallingScriptHash() != Runtime.getExecutingScriptHash()) {
            throw new Exception("No authorization!");
        }
        return true;
    }

}