package com.axlabs.neo.grantshares.util;

import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.types.Hash160;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProposalStruct {

    public int id;
    public Hash160 proposer;
    public int linkedProposal;
    public int acceptanceRate;
    public int quorum;
    public Hash160 endorser;
    public BigInteger reviewEnd;
    public BigInteger votingEnd;
    public BigInteger queuedEnd;
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
                list.get(6).getInteger(),
                list.get(7).getInteger(),
                list.get(8).getInteger(),
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
            Hash160 endorser, BigInteger reviewEnd, BigInteger votingEnd, BigInteger queuedEnd, boolean executed,
            List<IntentStruct> intents,
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

    public static class IntentStruct {
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