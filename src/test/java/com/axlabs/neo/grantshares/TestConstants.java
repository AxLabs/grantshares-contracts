package com.axlabs.neo.grantshares;

public class TestConstants {

    // Account names available in the neo-express config file.
    static final String ALICE = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    static final String BOB = "NZpsgXn9VQQoLexpuXJsrX8BsoyAhKUyiX";
    static final String CHARLIE = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";

    // contract methods
    static final String CREATE = "createProposal";
    static final String GET = "getProposal";
    static final String ENDORSE = "endorseProposal";
    static final String GET_PHASES = "getProposalPhases";
    static final String GET_VOTES = "getProposalVotes";
    static final String VOTE = "vote";
    static final String EXECUTE = "execute";
    static final String HASH_PROPOSAL = "hashProposal";
    static final String GET_PARAMETER = "getParameter";
    static final String CHANGE_PARAM = "changeParam";

    // events
    static final String PROPOSAL_CREATED = "ProposalCreated";
    static final String PROPOSAL_INTENT = "ProposalIntent";
    static final String PROPOSAL_ENDORSED = "ProposalEndorsed";
    static final String VOTED = "Voted";

    // governance parameters values
    static final int REVIEW_LENGTH = 5;
    static final int VOTING_LENGTH = 5;
    static final int QUEUED_LENGTH = 5;
    static final int MIN_ACCEPTANCE_RATE = 50;
    static final int MIN_QUORUM = 25;

    // parameter names
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String QUEUED_LENGTH_KEY = "queued_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MAX_FUNDING_AMOUNT_KEY = "max_funding";

    // Intent constants
    static final int MAX_METHOD_LEN = 256;
    static final int MAX_SERIALIZED_INTENT_PARAM_LEN = 1024;
}
