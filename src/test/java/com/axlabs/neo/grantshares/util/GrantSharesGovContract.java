package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.SmartContract;
import io.neow3j.contract.exceptions.UnexpectedReturnTypeException;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;
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

public class GrantSharesGovContract extends SmartContract {

    public GrantSharesGovContract(Hash160 scriptHash, Neow3j neow3j) {
        super(scriptHash, neow3j);
    }

    public StackItem getParameter(String paramName) throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName(), asList(string(paramName)))
                .getInvocationResult().getStack().get(0);
    }

    public Map<String, BigInteger> getParameters() throws IOException, UnexpectedReturnTypeException {
        Map<StackItem, StackItem> map = callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0)
                .getMap();
        return map.entrySet().stream().collect(Collectors.toMap(
                i -> i.getKey().getString(),
                i -> i.getValue().getInteger()));
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

    public TransactionBuilder createProposal(Hash160 proposer, String offchainUri, int linkedProposal,
            ContractParameter... intents) {
        return invokeFunction(getMethodName(), hash160(proposer), array(asList(intents)),
                string(offchainUri), integer(linkedProposal));
    }

    public TransactionBuilder createProposal(Hash160 proposer, String offchainUri, int linkedProposal,
            int acceptanceRate, int quorum, ContractParameter... intents) {
        return invokeFunction(getMethodName(), hash160(proposer), array(asList(intents)),
                string(offchainUri), integer(linkedProposal), integer(acceptanceRate), integer(quorum));
    }

    public TransactionBuilder endorseProposal(int id, Hash160 endorser) {
        return invokeFunction(getMethodName(), integer(id), hash160(endorser));
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

    public Date getProposalTimeLockEnd(int id) throws IOException {
        ProposalStruct proposal = getProposal(id);
        return new Date(proposal.timelockEnd.longValue());
    }

    public Date getProposalVoteEnd(int id) throws IOException {
        ProposalStruct proposal = getProposal(id);
        return new Date(proposal.votingEnd.longValue());
    }

    public Date getProposalReviewEnd(int id) throws IOException {
        ProposalStruct proposal = getProposal(id);
        return new Date(proposal.reviewEnd.longValue());
    }

    public void printProposalTimes(int id) throws IOException {
        ProposalStruct proposal = getProposal(id);
        if (proposal.endorser == null) {
            System.out.println("No phases available. Proposal is not endorsed yet.");
            return;
        }
        Date now = new Date();
        Date reviewEnd = getProposalReviewEnd(id);
        String reviewEnded = now.after(reviewEnd) ? "ENDED" : "OPEN";
        Date voteEnd = getProposalVoteEnd(id);
        String voteEnded = now.after(voteEnd) ? "ENDED" : "OPEN";
        Date timeLockEnd = getProposalTimeLockEnd(id);
        String timeLockEnded = now.after(timeLockEnd) ? "ENDED" : "OPEN";
        System.out.printf("\n### Proposal %s Phases:\n", id);
        System.out.println("Now:                 " + now);
        System.out.println("Review phase end:    " + reviewEnd + " -> " + reviewEnded);
        System.out.println("Voting phase end:    " + voteEnd + " -> " + voteEnded);
        System.out.println("Time lock phase end: " + timeLockEnd + " -> " + timeLockEnded + "\n");
    }

    public String calcMembersMultiSigAccount() throws IOException, UnexpectedReturnTypeException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getAddress();
    }

    public int calcMembersMultiSigAccountThreshold() throws IOException {
        return callInvokeFunction(getMethodName()).getInvocationResult().getStack().get(0).getInteger().intValue();
    }

    public Account getMembersAccount() throws IOException {
        int threshold = calcMembersMultiSigAccountThreshold();
        List<ECKeyPair.ECPublicKey> members = getMembers();
        return Account.createMultiSigAccount(members, threshold);
    }

    public TransactionBuilder updateContract(byte[] nef, String manifest, ContractParameter data) {
        if (data != null) {
            return invokeFunction(getMethodName(), byteArray(nef), string(manifest), data);
        } else {
            return invokeFunction(getMethodName(), byteArray(nef), string(manifest), any(null));
        }
    }

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }

}
