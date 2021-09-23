package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.constants.CallFlags;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
public class GrantSharesGov {

    static final StorageContext ctx = Storage.getStorageContext();

    static final StorageMap map = ctx.createMap(new byte[]{0x01});

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            map.put(new byte[]{0x01}, new ByteString("deployed!"));
        }
    }

    public static boolean putSomething(String key, String value) {
        Storage.put(ctx, key, value);
        return true;
    }

}
