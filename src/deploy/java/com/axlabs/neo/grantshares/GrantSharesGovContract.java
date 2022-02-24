package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.contract.exceptions.UnexpectedReturnTypeException;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;

public class GrantSharesGovContract extends SmartContract {

    public GrantSharesGovContract(Hash160 scriptHash, Neow3j neow3j) {
        super(scriptHash, neow3j);
    }

    public StackItem getParameter(String paramName) throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName(), asList(string(paramName)))
                .getInvocationResult().getStack().get(0);
    }

    public ProposalStruct getProposal(int id) throws IOException, UnexpectedReturnTypeException {
        List<StackItem> list = callInvokeFunction(getMethodName(), asList(integer(id))).getInvocationResult().getStack()
                .get(0).getList();
        return new ProposalStruct(list);
    }

    public List<ECPublicKey> getMembers() throws IOException, UnexpectedReturnTypeException {
        List<StackItem> list = callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getList();
        return list.stream().map(i -> new ECPublicKey(i.getByteArray())).collect(Collectors.toList());
    }

    public int getMembersCount() throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public int getProposalCount() throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public ProposalPaginatedStruct getProposals(int page, int itemsPerPage) throws IOException,
            UnexpectedReturnTypeException {
        List<StackItem> paginated = callInvokeFunction(getMethodName(), asList(integer(page), integer(itemsPerPage)))
                .getInvocationResult().getStack().get(0).getList();
        return new ProposalPaginatedStruct(paginated);
    }

    public boolean isPaused() throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getBoolean();
    }

    public TransactionBuilder createProposal(Hash160 proposer, String discussionUrl, int linkedProposal,
            IntentParam... intents) {
        return invokeFunction(getMethodName(), hash160(proposer), array(asList(intents)),
                string(discussionUrl), integer(linkedProposal));
    }

    public TransactionBuilder endorseProposal(int id, Hash160 proposer) {
        return invokeFunction(getMethodName(), integer(id), hash160(proposer));
    }

    public TransactionBuilder vote(int id, int vote, Hash160 voter) {
        return invokeFunction(getMethodName(), integer(id), integer(vote), hash160(voter));
    }

    public TransactionBuilder execute(int id) {
        return invokeFunction(getMethodName(), integer(id));
    }

    public TransactionBuilder pause() {
        return invokeFunction(getMethodName());
    }

    public TransactionBuilder unpause() {
        return invokeFunction(getMethodName());
    }

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }

}
