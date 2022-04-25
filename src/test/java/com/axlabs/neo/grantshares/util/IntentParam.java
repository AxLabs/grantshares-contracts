package com.axlabs.neo.grantshares.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.neow3j.contract.NefFile;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.ContractParameterType;
import io.neow3j.types.Hash160;

import java.math.BigInteger;

import static java.util.Arrays.asList;

public class IntentParam extends ContractParameter {

    public IntentParam(Hash160 targetContract, String method, ContractParameter... params) {
        super(ContractParameterType.ARRAY, new ContractParameter[]{
                hash160(targetContract), string(method), array(asList(params)), integer(CallFlags.ALL.getValue())});
    }

    public static IntentParam releaseTokenProposal(Hash160 treasury, Hash160 token, Hash160 receiver,
            BigInteger amount) {
        return new IntentParam(treasury, "releaseTokens",
                hash160(token), hash160(receiver), integer(amount));
    }

    public static IntentParam changeParamProposal(Hash160 gov, String paramName, int value) {
        return new IntentParam(gov, "changeParam",
                string(paramName), integer(value));
    }

    public static IntentParam addMemberProposal(Hash160 gov, ECPublicKey pubKey) {
        return new IntentParam(gov, "addMember", publicKey(pubKey));
    }

    public static IntentParam removeMemberProposal(Hash160 gov, ECPublicKey pubKey) {
        return new IntentParam(gov, "removeMember", publicKey(pubKey));
    }

    public static IntentParam updateContractProposal(Hash160 contract, NefFile nef, ContractManifest manifest)
            throws JsonProcessingException {
        String manifestString = ObjectMapperFactory.getObjectMapper().writeValueAsString(manifest);
        return new IntentParam(contract, "updateContract",
                byteArray(nef.toArray()), string(manifestString), any(null));
    }

    public static IntentParam addFunderProposal(Hash160 treasury, Hash160 accountHash, ECPublicKey... pubKeys) {
        return new IntentParam(treasury, "addFunder", hash160(accountHash), array(asList(pubKeys)));
    }

    public static IntentParam removeFunderProposal(Hash160 treasury, Hash160 accountHash) {
        return new IntentParam(treasury, "removeFunder", hash160(accountHash));
    }

    public static IntentParam removeWhitelistedTokenProposal(Hash160 treasury, Hash160 tokenHash) {
        return new IntentParam(treasury, "removeWhitelistedToken", hash160(tokenHash));
    }

    public static IntentParam addWhitelistedTokenProposal(Hash160 treasury, Hash160 tokenHash,
            BigInteger maxAmount) {
        return new IntentParam(treasury, "addWhitelistedToken", hash160(tokenHash), integer(maxAmount));
    }

    public static IntentParam setFundersMultiSigThresholdRatioProposal(Hash160 treasury, int newValue) {
        return new IntentParam(treasury, "setFundersMultiSigThresholdRatio", integer(newValue));
    }

    public static IntentParam drainProposal(Hash160 treasury) {
        return new IntentParam(treasury, "drain");
    }
}