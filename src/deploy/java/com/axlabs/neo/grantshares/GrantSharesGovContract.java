package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.contract.exceptions.UnexpectedReturnTypeException;
import io.neow3j.crypto.ECKeyPair.ECPublicKey;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.IntentParam.addFunderToTreasuryWhitelistProposal;
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

    public TransactionBuilder createProposalAddingTreasuryFunder(Hash160 proposer, ECPublicKey pubKey) {
        return createProposal(proposer, "nodiscussion", -1, addFunderToTreasuryWhitelistProposal(pubKey));
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
        return new Date(proposal.queuedEnd.longValue());
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

    private String getMethodName() {
        return new Exception().getStackTrace()[1].getMethodName();
    }

}
