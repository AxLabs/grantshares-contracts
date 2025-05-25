package com.axlabs.neo.grantshares.util.contracts;

import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;

@DisplayName("Storage")
public class Storage {

    static final StorageContext ctx = io.neow3j.devpack.Storage.getStorageContext();
    static final StorageMap dataMap = new StorageMap(ctx, 1);
    static final String COUNT_KEY = "count";

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            io.neow3j.devpack.Storage.put(ctx, COUNT_KEY, 0);
        }
    }

    /**
     * Stores the given byte array in the storage map with an ever-incrementing index.
     *
     * @param data The byte array to store
     * @return The index at which the data was stored
     */
    public static int store(ByteString data) {
        int currentCount = io.neow3j.devpack.Storage.getInt(ctx, COUNT_KEY);
        dataMap.put(currentCount, data);
        io.neow3j.devpack.Storage.put(ctx, COUNT_KEY, currentCount + 1);
        return currentCount;
    }

    /**
     * Stores the given byte array at a specific index in the storage map.
     * Note: This method does not update the count key.
     *
     * @param index The index at which to store the data
     * @param data  The byte array to store
     */
    public static void storeAtIndex(int index, ByteString data) {
        dataMap.put(index, data);
    }

    /**
     * Retrieves data stored at the given index.
     *
     * @param index The index to retrieve data from
     * @return The stored byte array
     */
    public static ByteString retrieve(int index) {
        return dataMap.get(index);
    }

    /**
     * Gets the total number of stored items.
     *
     * @return The count of stored items
     */
    public static int getCount() {
        return io.neow3j.devpack.Storage.getInt(ctx, COUNT_KEY);
    }
}
