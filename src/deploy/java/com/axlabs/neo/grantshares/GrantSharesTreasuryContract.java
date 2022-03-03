package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GrantSharesTreasuryContract extends SmartContract {

    public GrantSharesTreasuryContract(Hash160 scriptHash, Neow3j neow3j) {
        super(scriptHash, neow3j);
    }

    public boolean isPaused() throws IOException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getBoolean();
    }

    public Map<Hash160, BigInteger> getWhitelistedTokens() throws IOException {
        Map<StackItem, StackItem> map = callInvokeFunction(getMethodName())
                .getInvocationResult().getStack().get(0).getMap();
        return map.entrySet().stream().collect(Collectors.toMap(
                e -> Hash160.fromAddress(e.getKey().getAddress()),
                e -> e.getValue().getInteger())
        );
    }

    public List<ECPublicKey> getFunders() throws IOException {
        List<StackItem> stackItem = callInvokeFunction(getMethodName())
                .getInvocationResult().getStack().get(0).getList();
        return stackItem.stream()
                .map(StackItem::getByteArray)
                .map(ECPublicKey::new).collect(Collectors.toList());
    }

    public int getFundersCount() throws IOException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public Hash160 calcFundersMultiSigAccount() throws IOException {
        return Hash160.fromAddress(
                callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getAddress()
        );
    }

    public int calcFundersMultiSigAccountThreshold() throws IOException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public Account getFundersAccount() throws IOException {
        int threshold = calcFundersMultiSigAccountThreshold();
        List<ECPublicKey> funders = getFunders();
        return Account.createMultiSigAccount(funders, threshold);
    }

    public TransactionBuilder drain() {
        return invokeFunction(getMethodName());
    }

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }

}
