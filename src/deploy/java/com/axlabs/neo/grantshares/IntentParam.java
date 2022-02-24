package com.axlabs.neo.grantshares;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.neow3j.contract.NefFile;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.ContractParameterType;
import io.neow3j.types.Hash160;

import static java.util.Arrays.asList;

public class IntentParam extends ContractParameter {

    public IntentParam(Hash160 targetContract, String method, ContractParameter... params) {
        super(ContractParameterType.ARRAY, new ContractParameter[]{
                hash160(targetContract), string(method), array(asList(params))});
    }

    static IntentParam releaseTokenProposal(Hash160 token, Hash160 receiver, int amount) {
        return new IntentParam(Config.getGrantSharesTreasuryHash(), "releaseTokens",
                hash160(token), hash160(receiver), integer(amount));
    }

    static IntentParam changeParamProposal(String paramName, int value) {
        return new IntentParam(Config.getGrantSharesGovHash(), "changeParam",
                string(paramName), integer(value));
    }

    static IntentParam addMemberProposal(ECKeyPair.ECPublicKey pubKey) {
        return new IntentParam(Config.getGrantSharesGovHash(), "addMember", publicKey(pubKey));
    }

    static IntentParam removeMemberProposal(ECKeyPair.ECPublicKey pubKey) {
        return new IntentParam(Config.getGrantSharesGovHash(), "removeMember", publicKey(pubKey));
    }

    static IntentParam updateContractProposal(NefFile nef, ContractManifest manifest) throws JsonProcessingException {
        String manifestString = ObjectMapperFactory.getObjectMapper().writeValueAsString(manifest);
        return new IntentParam(Config.getGrantSharesGovHash(), "updateContract",
                byteArray(nef.toArray()), string(manifestString), any(null));
    }
}
