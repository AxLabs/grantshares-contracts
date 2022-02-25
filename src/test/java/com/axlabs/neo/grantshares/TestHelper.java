package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;

public class TestHelper {

    // Account names available in the neo-express config file.
    static final String ALICE = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    static final String BOB = "NZpsgXn9VQQoLexpuXJsrX8BsoyAhKUyiX";
    static final String CHARLIE = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";
    static final String DENISE = "NerDv9t8exrQRrP11jjvZKXzSXvTnmfDTo";
    static final String EVE = "NZ539Rd57v5NEtAdkHyFGaWj1uGt2DecUL";
    static final String FLORIAN = "NRy5bp81kScYFZHLfMBXuubFfRyboVyu7G";

    // GrantSharesGov contract methods
    static final String CREATE = "createProposal";
    static final String GET_PROPOSAL = "getProposal";
    static final String GET_PROPOSALS = "getProposals";
    static final String GET_PARAMETER = "getParameter";
    static final String GET_MEMBERS = "getMembers";
    static final String GET_MEMBERS_COUNT = "getMembersCount";
    static final String GET_PROPOSAL_COUNT = "getProposalCount";
    static final String PAUSE = "pause";
    static final String UNPAUSE = "unpause";
    static final String IS_PAUSED = "isPaused";
    static final String CALC_MEMBER_MULTI_SIG_ACC = "calcMembersMultiSigAccount";

    // GrantSharesTreasury contract methods
    static final String GET_FUNDERS = "getFunders";
    static final String GET_WHITELISTED_TOKENS = "getWhitelistedTokens";
    static final String ADD_FUNDER = "addFunder";
    static final String REMOVE_FUNDER = "removeFunder";
    static final String ADD_WHITELISTED_TOKEN = "addWhitelistedToken";
    static final String REMOVE_WHITELISTED_TOKEN = "removeWhitelistedToken";
    static final String RELEASE_TOKENS = "releaseTokens";
    static final String DRAIN = "drain";
    static final String CHANGE_THRESHOLD = "changeThreshold";
    static final String CALC_FUNDERS_MULTI_SIG_ACCOUNT = "calcFundersMultiSigAccount";

    static final String ENDORSE = "endorseProposal";
    static final String VOTE = "vote";
    static final String EXECUTE = "execute";
    static final String CHANGE_PARAM = "changeParam";
    static final String ADD_MEMBER = "addMember";
    static final String REMOVE_MEMBER = "removeMember";
    static final String UPDATE_CONTRACT = "updateContract";

    // events
    static final String PROPOSAL_CREATED = "ProposalCreated";
    static final String PROPOSAL_ENDORSED = "ProposalEndorsed";
    static final String PROPOSAL_EXECUTED = "ProposalExecuted";
    static final String VOTED = "Voted";
    static final String MEMBER_ADDED = "MemberAdded";
    static final String MEMBER_REMOVED = "MemberRemoved";
    static final String PARAMETER_CHANGED = "ParameterChanged";

    // governance parameters values
    static final int PHASE_LENGTH = 10; // blocks instead of time for testing
    static final int MIN_ACCEPTANCE_RATE = 50;
    static final int MIN_QUORUM = 50;
    static final int MULTI_SIG_THRESHOLD_RATIO = 50;

    // parameter names
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String MULTI_SIG_THRESHOLD_KEY = "threshold";

    static MessageDigest hasher;

    static {
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    static ContractParameter prepareDeployParameter(Account... members) {
        return array(
                array(Arrays.stream(members)
                        .map(m -> publicKey(m.getECKeyPair().getPublicKey().getEncoded(true)))
                        .collect(Collectors.toList())),
                array(
                        REVIEW_LENGTH_KEY, PHASE_LENGTH,
                        VOTING_LENGTH_KEY, PHASE_LENGTH,
                        TIMELOCK_LENGTH_KEY, PHASE_LENGTH,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM,
                        MULTI_SIG_THRESHOLD_KEY, MULTI_SIG_THRESHOLD_RATIO
                )
        );
    }

    static Hash256 createSimpleProposal(SmartContract contract, Account proposer,
            String discussionUrl) throws Throwable {

        return contract.invokeFunction(CREATE, hash160(proposer),
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash()))
                                )
                        ),
                        string(discussionUrl),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
    }


    static int createAndEndorseProposal(SmartContract contract, Neow3j neow3j, Account proposer,
            Account endorser, ContractParameter intents, String discussionUrl) throws Throwable {
        // 1. create proposal
        TransactionBuilder b = contract.invokeFunction(CREATE, hash160(proposer), intents, string(discussionUrl),
                integer(-1));
        return sendAndEndorseProposal(contract, neow3j, proposer, endorser, b);
    }

    static int createAndEndorseProposal(SmartContract contract, Neow3j neow3j, Account proposer,
            Account endorser, ContractParameter intents, String discussionUrl, int acceptanceRate, int quorum)
            throws Throwable {
        // 1. create proposal
        TransactionBuilder b = contract.invokeFunction(CREATE, hash160(proposer), intents, string(discussionUrl),
                integer(-1), integer(acceptanceRate), integer(quorum));

        return sendAndEndorseProposal(contract, neow3j, proposer, endorser, b);
    }

    private static int sendAndEndorseProposal(SmartContract contract, Neow3j neow3j, Account proposer,
            Account endorser, TransactionBuilder b) throws Throwable {
        Hash256 tx = b.signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        int id = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. endorse proposal
        tx = contract.invokeFunction(ENDORSE, integer(id), hash160(endorser))
                .signers(AccountSigner.calledByEntry(endorser))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        return id;
    }

    static void voteForProposal(SmartContract contract, Neow3j neow3j, int id,
            Account endorserAndVoter) throws Throwable {
        Hash256 tx = contract.invokeFunction(VOTE, integer(id), integer(1),
                        hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    static void voteForProposal(SmartContract contract, Neow3j neow3j, int id, int vote,
            Account endorserAndVoter) throws Throwable {
        Hash256 tx = contract.invokeFunction(VOTE, integer(id), integer(vote),
                        hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    static Account createMultiSigAccount(int threshold, Account... accounts) {
        List<ECKeyPair.ECPublicKey> pubKeys = Arrays.stream(accounts)
                .map(a -> a.getECKeyPair().getPublicKey())
                .collect(Collectors.toList());
        return Account.createMultiSigAccount(pubKeys, threshold);
    }
}
