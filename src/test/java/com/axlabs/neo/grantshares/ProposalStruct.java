package com.axlabs.neo.grantshares;

import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.string;

public class ProposalStruct {

    public int id;
    public Hash160 proposer;
    public int linkedProposal;
    public int acceptanceRate;
    public int quorum;
    public Hash160 endorser;
    public int reviewEnd;
    public int votingEnd;
    public int queuedEnd;
    public boolean executed;
    public List<IntentStruct> intents;
    public String discussionUrl;
    public int approve;
    public int reject;
    public int abstain;
    Map<StackItem, StackItem> voters;

    public ProposalStruct(List<StackItem> list) {
        this(
                list.get(0).getInteger().intValue(),
                Hash160.fromAddress(list.get(1).getAddress()),
                list.get(2).getInteger().intValue(),
                list.get(3).getInteger().intValue(),
                list.get(4).getInteger().intValue(),
                list.get(5).getValue() == null ? null : Hash160.fromAddress(list.get(5).getAddress()),
                list.get(6).getInteger().intValue(),
                list.get(7).getInteger().intValue(),
                list.get(8).getInteger().intValue(),
                list.get(9).getBoolean(),
                list.get(10).getList().stream().map(i -> new IntentStruct(i.getList())).collect(Collectors.toList()),
                list.get(11).getString(),
                list.get(12).getInteger().intValue(),
                list.get(13).getInteger().intValue(),
                list.get(14).getInteger().intValue(),
                list.get(15).getMap() // voters
        );
    }

    public ProposalStruct(int id, Hash160 proposer, int linkedProposal, int acceptanceRate, int quorum,
            Hash160 endorser, int reviewEnd, int votingEnd, int queuedEnd, boolean executed, List<IntentStruct> intents,
            String discussionUrl, int approve, int reject, int abstain, Map<StackItem, StackItem> voters) {
        this.id = id;
        this.proposer = proposer;
        this.linkedProposal = linkedProposal;
        this.acceptanceRate = acceptanceRate;
        this.quorum = quorum;
        this.endorser = endorser;
        this.reviewEnd = reviewEnd;
        this.votingEnd = votingEnd;
        this.queuedEnd = queuedEnd;
        this.executed = executed;
        this.intents = intents;
        this.discussionUrl = discussionUrl;
        this.approve = approve;
        this.reject = reject;
        this.abstain = abstain;
        this.voters = voters;
    }

    static class IntentStruct {
        public Hash160 targetContract;
        public String method;
        public List<StackItem> params;

        public IntentStruct(List<StackItem> list) {
            this(
                    Hash160.fromAddress(list.get(0).getAddress()),
                    list.get(1).getString(),
                    list.get(2).getList()
            );
        }

        public IntentStruct(Hash160 targetContract, String method, List<StackItem> params) {
            this.targetContract = targetContract;
            this.method = method;
            this.params = params;
        }
    }
}
