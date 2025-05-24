package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.SmartContract;
import io.neow3j.contract.exceptions.UnexpectedReturnTypeException;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;

import java.io.IOException;

import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.integer;
import static java.util.Arrays.asList;

public class StorageContract extends SmartContract {

    public StorageContract(Hash160 scriptHash, Neow3j neow3j) {
        super(scriptHash, neow3j);
    }

    /**
     * Store data at the next available index.
     *
     * @param data The data to store
     * @return A TransactionBuilder that can be used to sign and send the transaction
     */
    public TransactionBuilder store(byte[] data) {
        return invokeFunction(getMethodName(), byteArray(data));
    }

    /**
     * Store data at a specific index.
     *
     * @param index The index at which to store the data
     * @param data  The data to store
     * @return A TransactionBuilder that can be used to sign and send the transaction
     */
    public TransactionBuilder storeAtIndex(int index, byte[] data) {
        return invokeFunction(getMethodName(), integer(index), byteArray(data));
    }

    /**
     * Retrieve data from a specific index.
     *
     * @param index The index from which to retrieve data
     * @return The stored data as a byte array
     * @throws IOException                   if there is an error communicating with the network
     * @throws UnexpectedReturnTypeException if the returned data is not in the expected format
     */
    public byte[] retrieve(int index) throws IOException, UnexpectedReturnTypeException {
        InvocationResult result = callInvokeFunction(getMethodName(), asList(integer(index))).getInvocationResult();
        if (result.getStack().isEmpty()) {
            return null;
        }
        return result.getStack().get(0).getByteArray();
    }

    /**
     * Get the total number of stored items.
     *
     * @return The count of stored items
     * @throws IOException                   if there is an error communicating with the network
     * @throws UnexpectedReturnTypeException if the returned data is not in the expected format
     */
    public int getCount() throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName())
                .getInvocationResult()
                .getStack()
                .get(0)
                .getInteger()
                .intValue();
    }

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }
}
