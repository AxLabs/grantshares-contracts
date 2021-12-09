package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.contracts.LedgerContract;
import io.neow3j.devpack.events.Event1Arg;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event5Args;

import static io.neow3j.devpack.Helper.memcpy;
import static io.neow3j.devpack.Runtime.checkWitness;
import static io.neow3j.devpack.contracts.CryptoLib.sha256;
import static io.neow3j.devpack.contracts.LedgerContract.currentIndex;
import static io.neow3j.devpack.contracts.StdLib.deserialize;
import static io.neow3j.devpack.contracts.StdLib.serialize;

@ManifestExtra(key = "name", value = "GrantShares")
@Permission(contract = "*", methods = "*")
@SuppressWarnings("unchecked")
public class GrantSharesGov {

    //region CONTRACT VARIABLES
    // Constants
    static final int MAX_METHOD_LEN = 128;
    static final int MAX_SERIALIZED_INTENT_PARAM_LEN = 1024;

    // Storage, Keys, Prefixes
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String QUEUED_LENGTH_KEY = "queued_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MAX_FUNDING_AMOUNT_KEY = "max_funding";
    static final String NR_OF_MEMBERS_KEY = "#_members";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap proposals = ctx.createMap((byte) 1);
    static final StorageMap proposalVotes = ctx.createMap(2);
    static final StorageMap proposalPhases = ctx.createMap(3);
    static final StorageMap parameters = ctx.createMap(4);
    // Maps member hashes to the block index at which they became a member.
    static final StorageMap members = ctx.createMap(5);
    //endregion CONTRACT VARIABLES

    //region EVENTS
    @DisplayName("ProposalCreated")
    static Event5Args<ByteString, Hash160, ByteString, Integer, Integer> created;
    @DisplayName("ProposalIntent")
    static Event2Args<ByteString, Intent> intent;
    @DisplayName("ProposalEndorsed")
    static Event5Args<ByteString, Hash160, Integer, Integer, Integer> endorsed;
    @DisplayName("Voted")
    static Event3Args<ByteString, Hash160, Integer> voted;
    @DisplayName("ProposalExecuted")
    static Event1Arg<ByteString> executed;
    @DisplayName("MemberAdded")
    static Event1Arg<Hash160> memberAdded;
    @DisplayName("MemberRemoved")
    static Event1Arg<Hash160> memberRemoved;
    @DisplayName("ParameterChanged")
    static Event2Args<String, byte[]> paramChanged;
    //endregion EVENTS

    @OnDeployment
    @SuppressWarnings("unchecked")
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            List<Object> membersAndParams = (List<Object>) data;
            List<Hash160> initialMembers = (List<Hash160>) membersAndParams.get(0);
            int blockIdx = currentIndex();
            for (int i = 0; i < initialMembers.size(); i++) {
                // TODO: Any information we should map the members to?
                members.put(initialMembers.get(i).toByteString(), blockIdx);
            }
            Storage.put(ctx, NR_OF_MEMBERS_KEY, initialMembers.size());

            List<Object> params = (List<Object>) membersAndParams.get(1);
            for (int i = 0; i < params.size(); i += 2) {
                parameters.put((ByteString) params.get(i), (int) params.get(i + 1));
            }
            // TODO: Consider failing if not all parameters were set.
        }
    }

    //region SAFE METHODS
    @Safe
    public static ByteString hashProposal(Intent[] intents, ByteString descriptionHash) {
        ByteString b = new ByteString("");
        for (Intent i : intents) {
            // Concatenate target contract
            assert Hash160.isValid(i.targetContract)
                    : "GrantSharesGov: Invalid target contract hash";
            b = b.concat(i.targetContract.toByteString());

            // Pad and concatenate target method
            byte[] paddedMethod = new byte[MAX_METHOD_LEN];
            ByteString methodBytes = new ByteString(i.method);
            assert methodBytes.length() <= GrantSharesGov.MAX_METHOD_LEN
                    : "GrantSharesGov: Target method name too long";
            memcpy(paddedMethod, 0, methodBytes, 0, methodBytes.length());
            b = b.concat(paddedMethod);

            // Pad and concatenate method parameters
            for (Object p : i.params) {
                // TODO: Consider breaking compound stack items (e.g., list or map) down and
                //  serializing their parts with a smaller padded byte array size.
                byte[] paddedParam = new byte[MAX_SERIALIZED_INTENT_PARAM_LEN];
                ByteString paramBytes = serialize(p);
                assert paramBytes.length() <= MAX_SERIALIZED_INTENT_PARAM_LEN
                        : "GrantSharesGov: Intent method parameter too big";
                memcpy(paddedParam, 0, paramBytes, 0, paramBytes.length());
                b = b.concat(paddedParam);
            }
        }
        return sha256(b.concat(descriptionHash));
    }

    @Safe
    public static Object getParameter(String paramName) {
        return parameters.get(paramName);
    }

    @Safe
    public static Proposal getProposal(ByteString proposalHash) {
        ByteString bytes = proposals.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (Proposal) deserialize(proposals.get(proposalHash));
    }

    @Safe
    public static ProposalVotes getProposalVotes(ByteString proposalHash) {
        ByteString bytes = proposalVotes.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (ProposalVotes) deserialize(proposalVotes.get(proposalHash));
    }

    @Safe
    public static ProposalPhases getProposalPhases(ByteString proposalHash) {
        ByteString bytes = proposalPhases.get(proposalHash);
        if (bytes == null) {
            return null;
        }
        return (ProposalPhases) deserialize(proposalPhases.get(proposalHash));
    }
    //endregion SAFE METHODS

    /**
     * Creates a proposal with the default settings for the acceptance rate and quorum.
     *
     * @param proposer        The account set as the proposer.
     * @param intents         The intents to be executed when the proposal is accepted.
     * @param descriptionHash A SHA-256 of the proposal's description.
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents,
            ByteString descriptionHash, ByteString linkedProposal) {

        return createProposal(
                proposer,
                intents,
                descriptionHash,
                linkedProposal,
                parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY),
                parameters.getInteger(MIN_QUORUM_KEY));
    }

    /**
     * Creates a proposal.
     *
     * @param proposer        The account set as the proposer.
     * @param intents         The intents to be executed when the proposal is accepted.
     * @param descriptionHash A SHA-256 of the proposal's description.
     * @param linkedProposal  A proposal that preceded this one.
     * @param acceptanceRate  The desired acceptance rate.
     * @param quorum          The desired quorum.
     * @return The hash of the proposal.
     * @throws Exception if proposal already exists; if the invoking transaction does not hold a
     *                   witness for the proposer.
     */
    public static ByteString createProposal(Hash160 proposer, Intent[] intents,
            ByteString descriptionHash, ByteString linkedProposal, int acceptanceRate, int quorum) {

        assert checkWitness(proposer) : "GrantSharesGov: Not authorised";
        assert acceptanceRate >= parameters.getInteger(MIN_ACCEPTANCE_RATE_KEY)
                : "GrantSharesGov: Acceptance rate not allowed";
        assert quorum >= parameters.getInteger(MIN_QUORUM_KEY)
                : "GrantSharesGov: Quorum not allowed";
        assert linkedProposal == null || proposals.get(linkedProposal) != null
                : "GrantSharesGov: Linked proposal doesn't exist";

        ByteString proposalHash = hashProposal(intents, descriptionHash);
        ByteString proposalBytes = proposals.get(proposalHash.toByteArray());
        // TODO: Consider also checking the phase of an already existing proposal and allow
        //  creation if the proposal if the existing one was executed.
        assert proposalBytes == null : "GrantSharesGov: Proposal already exists";

        proposals.put(proposalHash, serialize(new Proposal(proposalHash, proposer,
                linkedProposal, acceptanceRate, quorum)));

        // An event cannot take a large amount of data, i.e., we should not pass the description,
        // since it might be too large. The max size of a state item is 1024 bytes.
        created.fire(proposalHash, proposer, descriptionHash, acceptanceRate, quorum);
        for (Intent i : intents) {
            intent.fire(proposalHash, i);
        }

        return proposalHash;
    }

    /**
     * If the {@code endorser} is a DAO member and the proposal is an existing un-endorsed
     * proposal, it becomes endorsed and its phases are set.
     *
     * @param proposalHash The proposal to endorse.
     * @param endorser     The script hash of the endorsing DAO member.
     */
    public static void endorseProposal(ByteString proposalHash, Hash160 endorser) {
        assert members.get(endorser.toByteString()) != null && checkWitness(endorser)
                : "GrantSharesGov: Not authorised";
        assert proposals.get(proposalHash) != null
                : "GrantSharesGov: Proposal doesn't exist";
        assert proposalPhases.get(proposalHash) == null
                : "GrantSharesGov: Proposal already endorsed";

        // Add +1 because the current idx is the block before this execution happens.
        int reviewEnd = currentIndex() + 1 + parameters.getInteger(REVIEW_LENGTH_KEY);
        int votingEnd = reviewEnd + parameters.getInteger(VOTING_LENGTH_KEY);
        int queuedEnd = votingEnd + parameters.getInteger(QUEUED_LENGTH_KEY);
        // TODO: Consider adding the endroser on the proposal, which makes it more convenient for
        //  the outside to get that information, instead of catching the event below.
        proposalPhases.put(proposalHash, serialize(
                new ProposalPhases(reviewEnd, votingEnd, queuedEnd)));

        proposalVotes.put(proposalHash, serialize(new ProposalVotes()));
        // TODO: Consider removing the phase ends from the event. They are fetchable from the
        //  contract via getProposalPhases.
        endorsed.fire(proposalHash, endorser, reviewEnd, votingEnd, queuedEnd);
    }

    /**
     * Casts a vote of the {@code voter} on the proposal with {@code proposalHash}.
     *
     * @param proposalHash The hash of the proposal to vote on.
     * @param vote         The vote. Must be either -1 for rejecting, 1 for approving or 0 for
     *                     abstaining.
     * @param voter        The script hash of the voter. Must be a member of the DAO and the
     *                     invoking script must hold a witness of the voter.
     * @throws Exception if the voter is not a DAO member, no witness for the voter is found, the
     *                   proposal does not exist or the proposal is not in its voting phase.
     */
    public static void vote(ByteString proposalHash, int vote, Hash160 voter) {
        assert vote >= -1 && vote <= 1 : "GrantSharesGov: Illegal vote. Right to jail!";
        assert members.get(voter.toByteString()) != null && checkWitness(voter)
                : "GrantSharesGov: Not authorised";
        ByteString ppBytes = proposalPhases.get(proposalHash);
        assert ppBytes != null : "GrantSharesGov: Proposal doesn't exist or wasn't endorsed yet";
        ProposalPhases pp = (ProposalPhases) deserialize(ppBytes);
        int currentIdx = currentIndex();
        assert currentIdx >= pp.reviewEnd && currentIdx < pp.votingEnd
                : "GrantSharesGov: Proposal not active";

        // No need for null check. This map was created in the endorsement and we know the
        // endorsement happened because the proposal phases were set.
        ProposalVotes pv = (ProposalVotes) deserialize(proposalVotes.get(proposalHash));
        if (vote < 0) {
            pv.reject += 1;
        } else if (vote > 0) {
            pv.approve += 1;
        } else {
            pv.abstain += 1;
        }
        proposalVotes.put(proposalHash, serialize(pv));
        voted.fire(proposalHash, voter, vote);
    }

    /**
     * Executes the proposal with the given {@code intents} and {@code descriptionHash}. Anyone
     * can execute a proposal.
     * <p>
     * Execution is only successful if the proposal is out of its queued phase, was accepted and
     * does not have a connected abrogation proposal that was accepted.
     *
     * @param intents         The intents of the proposal.
     * @param descriptionHash The hash of the proposal description.
     * @return the values returned the called intents.
     */
    public static Object[] execute(Intent[] intents, ByteString descriptionHash) {
        ByteString proposalHash = hashProposal(intents, descriptionHash);
        ByteString proposalBytes = proposals.get(proposalHash);
        assert proposalBytes != null : "GrantSharesGov: Proposal doesn't exist";
        ByteString proposalPhasesBytes = proposalPhases.get(proposalHash);
        ByteString proposalVotesBytes = proposalVotes.get(proposalHash);
        assert proposalPhasesBytes != null && proposalVotesBytes != null
                : "GrantSharesGov: Proposal wasn't endorsed yet";
        ProposalPhases pp = (ProposalPhases) deserialize(proposalPhasesBytes);
        assert currentIndex() >= pp.queuedEnd : "GrantSharesGov: Proposal not in execution phase";

        ProposalVotes pv = (ProposalVotes) deserialize(proposalVotesBytes);
        Proposal p = (Proposal) deserialize(proposalBytes);
        int participation = pv.approve + pv.abstain + pv.reject;
        assert participation * 100 / Storage.getInteger(ctx, NR_OF_MEMBERS_KEY) >= p.quorum
                : "GrantSharesGov: Quorum not reached";
        assert pv.approve * 100 / participation >= p.acceptanceRate
                : "GrantSharesGov: Proposal rejected";

        // TODO: Check if has abrogation proposal that was accepted.

        Object[] returnVals = new Object[intents.length];
        for (int i = 0; i < intents.length; i++) {
            Intent t = intents[i];
            returnVals[i] = Contract.call(t.targetContract, t.method, CallFlags.All, t.params);
        }
        executed.fire(proposalHash);
        return returnVals;
    }

    // TODO:
    //  - leave()
    //  - claimFunds() maybe add this to the treasury

    //region PROPOSAL-INVOKED METHODS
    // TODO: Consider adding specific setters for each DAO parameter.
    public static void changeParam(String paramKey, Object value) {
        assertCallerIsSelf();
        parameters.put(paramKey, (byte[]) value);
        paramChanged.fire(paramKey, (byte[]) value);
    }

    // TODO: Test
    public static void addMember(Hash160 member) {
        assertCallerIsSelf();
        assert Hash160.isValid(member) : "GrantSharesGov: Not a valid account hash";
        ByteString blockIndexBytes = members.get(member.toByteString());
        assert blockIndexBytes == null : "GrantSharesGov: Already a member";
        members.put(member.toByteString(), LedgerContract.currentIndex());
        memberAdded.fire(member);
    }

    // TODO: Test
    public static void removeMember(Hash160 member) {
        assertCallerIsSelf();
        assert Hash160.isValid(member) : "GrantSharesGov: Not a valid account hash";
        ByteString blockIndexBytes = members.get(member.toByteString());
        assert blockIndexBytes != null : "GrantSharesGov: Not a member";
        members.delete(member.toByteString());
        memberRemoved.fire(member);
    }
    //endregion PROPOSAL-INVOKED METHODS

    static void assertCallerIsSelf() {
        assert Runtime.getCallingScriptHash() == Runtime.getExecutingScriptHash() :
                "GrantSharesGov: Method only callable by the contract itself";
    }


}