package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;

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

    public Map<Hash160, List<ECPublicKey>> getFunders() throws IOException {
        Map<StackItem, StackItem> stackItem = callInvokeFunction(getMethodName())
                .getInvocationResult().getStack().get(0).getMap();
        Map<Hash160, List<ECPublicKey>> map = new HashMap<>();
        stackItem.entrySet().forEach(e -> {
            Hash160 hash = Hash160.fromAddress(e.getKey().getAddress());
            List<ECPublicKey> publicKeys = e.getValue().getList().stream()
                    .map(k -> new ECPublicKey(k.getByteArray())).collect(Collectors.toList());
            map.put(hash, publicKeys);
        });
        return map;
    }

    public List<ECPublicKey> getFunderPublicKeys() throws IOException {
        return getFunders().values().stream().reduce((a, b) -> {
            a.addAll(b);
            return a;
        }).get();
    }

    public Hash160 calcFundersMultiSigAddress() throws IOException {
        return Hash160.fromAddress(
                callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getAddress()
        );
    }

    public int calcFundersMultiSigAddressThreshold() throws IOException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public Account getFundersMultiSigAccount() throws IOException {
        int threshold = calcFundersMultiSigAddressThreshold();
        List<ECPublicKey> funders = getFunderPublicKeys();
        return Account.createMultiSigAccount(funders, threshold);
    }

    public TransactionBuilder drain() {
        return invokeFunction(getMethodName());
    }

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }

    public TransactionBuilder addFunder(Hash160 accountHash, ECPublicKey... publicKeys) {
        return invokeFunction(getMethodName(), hash160(accountHash), array(asList(publicKeys)));
    }

    public int setFundersMultiSigThresholdRatio() throws IOException {
        return callFunctionReturningInt(getMethodName()).intValue();
    }

    public int getFundersMultiSigThresholdRatio() throws IOException {
        return callFunctionReturningInt(getMethodName()).intValue();
    }

    public TransactionBuilder removeFunder(Hash160 funderHash) {
        return invokeFunction(getMethodName(), hash160(funderHash));
    }

    public TransactionBuilder addWhitelistedToken(Hash160 tokenHash, int maxFundingAmount) {
        return invokeFunction(getMethodName(), hash160(tokenHash), integer(maxFundingAmount));
    }

    public TransactionBuilder removeWhitelistedToken(Hash160 tokenHash) {
        return invokeFunction(getMethodName(), hash160(tokenHash));
    }

    public TransactionBuilder releaseTokens(Hash160 tokenHash, Hash160 receiverHash, BigInteger amount) {
        return invokeFunction(getMethodName(), hash160(tokenHash), hash160(receiverHash), integer(amount));
    }

    public TransactionBuilder voteCommitteeMemberWithLeastVotes() {
        return invokeFunction(getMethodName());
    }

    public TransactionBuilder updateContract(byte[] nef, String manifest, Object data) {
        return invokeFunction(getMethodName(), byteArray(nef), string(manifest), any(data));
    }

}
