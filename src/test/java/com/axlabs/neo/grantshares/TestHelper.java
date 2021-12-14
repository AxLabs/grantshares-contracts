package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static java.nio.charset.StandardCharsets.UTF_8;

public class TestHelper {

    // Account names available in the neo-express config file.
    static final String ALICE = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    static final String BOB = "NZpsgXn9VQQoLexpuXJsrX8BsoyAhKUyiX";
    static final String CHARLIE = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";

    // contract methods
    static final String CREATE = "createProposal";
    static final String GET_PROPOSAL = "getProposal";
    static final String GET_PROPOSALS = "getProposals";
    static final String GET_PHASES = "getProposalPhases";
    static final String GET_VOTES = "getProposalVotes";
    static final String GET_PARAMETER = "getParameter";
    static final String GET_MEMBERS = "getMembers";
    static final String GET_NR_OF_PROPOSALS = "getNrOfProposals";

    static final String ENDORSE = "endorseProposal";
    static final String VOTE = "vote";
    static final String EXECUTE = "execute";
    static final String HASH_PROPOSAL = "hashProposal";
    static final String CHANGE_PARAM = "changeParam";
    static final String ADD_MEMBER = "addMember";
    static final String REMOVE_MEMBER = "removeMember";

    // events
    static final String PROPOSAL_CREATED = "ProposalCreated";
    static final String PROPOSAL_INTENT = "ProposalIntent";
    static final String PROPOSAL_ENDORSED = "ProposalEndorsed";
    static final String PROPOSAL_EXECUTED = "ProposalExecuted";
    static final String VOTED = "Voted";
    static final String MEMBER_ADDED = "MemberAdded";
    static final String MEMBER_REMOVED = "MemberRemoved";
    static final String PARAMETER_CHANGED = "ParameterChanged";

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
    static final int MAX_METHOD_LEN = 128;
    static final int MAX_SERIALIZED_INTENT_PARAM_LEN = 1024;

    static MessageDigest hasher;

    static {
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    static ContractParameter prepareDeployParameter(Hash160... govMembers) throws Exception {
        return array(
                array(govMembers),
                array(
                        REVIEW_LENGTH_KEY, REVIEW_LENGTH,
                        VOTING_LENGTH_KEY, VOTING_LENGTH,
                        QUEUED_LENGTH_KEY, QUEUED_LENGTH,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM
                )
        );
    }

    static Hash256 createSimpleProposal(SmartContract contract, Account proposer,
            String description) throws Throwable {

        return contract.invokeFunction(CREATE, hash160(proposer),
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))
                                )
                        ),
                        byteArray(hasher.digest(description.getBytes(UTF_8))),
                        any(null))
                .signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
    }


    static String createAndEndorseProposal(SmartContract contract, Neow3j neow3j, Account proposer,
            Account endorser, ContractParameter intents, String description) throws Throwable {

        // 1. create proposal
        Hash256 tx = contract.invokeFunction(CREATE, hash160(proposer),
                        intents, byteArray(hasher.digest(description.getBytes(UTF_8))), any(null))
                .signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        String proposalHash = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getHexString();

        // 2. endorse proposal
        tx = contract.invokeFunction(ENDORSE, byteArray(proposalHash), hash160(endorser))
                .signers(AccountSigner.calledByEntry(endorser))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        return proposalHash;
    }

    static void voteForProposal(SmartContract contract, Neow3j neow3j, String proposalHash,
            Account endorserAndVoter) throws Throwable {
        Hash256 tx = contract.invokeFunction(VOTE, byteArray(proposalHash), integer(1),
                        hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }
}
