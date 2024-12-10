package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.ECKeyPair;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.IOException;
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

public class TestHelper {

    // Account names available in the neo-express config file.
    public static final String ALICE = "NM7Aky765FG8NhhwtxjXRx7jEL1cnw7PBP";
    public static final String BOB = "NZpsgXn9VQQoLexpuXJsrX8BsoyAhKUyiX";
    public static final String CHARLIE = "NdbtgSku2qLuwsBBzLx3FLtmmMdm32Ktor";
    public static final String DENISE = "NerDv9t8exrQRrP11jjvZKXzSXvTnmfDTo";
    public static final String EVE = "NZ539Rd57v5NEtAdkHyFGaWj1uGt2DecUL";
    public static final String FLORIAN = "NRy5bp81kScYFZHLfMBXuubFfRyboVyu7G";

    // GrantSharesGov contract methods
    public static final String CREATE = "createProposal";
    public static final String GET_PROPOSAL = "getProposal";
    public static final String GET_PROPOSALS = "getProposals";
    public static final String GET_PARAMETER = "getParameter";
    public static final String GET_MEMBERS = "getMembers";
    public static final String GET_MEMBERS_COUNT = "getMembersCount";
    public static final String GET_PROPOSAL_COUNT = "getProposalCount";
    public static final String PAUSE = "pause";
    public static final String UNPAUSE = "unpause";
    public static final String IS_PAUSED = "isPaused";
    public static final String CALC_MEMBER_MULTI_SIG_ACC = "calcMembersMultiSigAccount";
    public static final String ENDORSE = "endorseProposal";
    public static final String VOTE = "vote";
    public static final String EXECUTE = "execute";
    public static final String CHANGE_PARAM = "changeParam";
    public static final String ADD_MEMBER = "addMember";
    public static final String REMOVE_MEMBER = "removeMember";
    public static final String UPDATE_CONTRACT = "updateContract";

    // events
    public static final String PROPOSAL_CREATED = "ProposalCreated";
    public static final String PROPOSAL_ENDORSED = "ProposalEndorsed";
    public static final String PROPOSAL_EXECUTED = "ProposalExecuted";
    public static final String VOTED = "Voted";
    public static final String MEMBER_ADDED = "MemberAdded";
    public static final String MEMBER_REMOVED = "MemberRemoved";
    public static final String PARAMETER_CHANGED = "ParameterChanged";

    // governance parameters values
    public static final int PHASE_LENGTH = 60; // seconds
    public static final int MIN_ACCEPTANCE_RATE = 50;
    public static final int MIN_QUORUM = 50;
    public static final int MULTI_SIG_THRESHOLD_RATIO = 50;

    // parameter names
    public static final String REVIEW_LENGTH_KEY = "review_len";
    public static final String VOTING_LENGTH_KEY = "voting_len";
    public static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    public static final String EXPIRATION_LENGTH_KEY = "expiration_len";
    public static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    public static final String MIN_QUORUM_KEY = "min_quorum";
    public static final String MULTI_SIG_THRESHOLD_KEY = "threshold";

    static MessageDigest hasher;

    static {
        try {
            hasher = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    public static ContractParameter prepareDeployParameter(Account... members) {
        return array(
                array(Arrays.stream(members)
                        .map(m -> publicKey(m.getECKeyPair().getPublicKey().getEncoded(true)))
                        .collect(Collectors.toList())),
                array(
                        REVIEW_LENGTH_KEY, PHASE_LENGTH * 1000,
                        VOTING_LENGTH_KEY, PHASE_LENGTH * 1000,
                        TIMELOCK_LENGTH_KEY, PHASE_LENGTH * 1000,
                        EXPIRATION_LENGTH_KEY, PHASE_LENGTH * 1000,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM,
                        MULTI_SIG_THRESHOLD_KEY, MULTI_SIG_THRESHOLD_RATIO
                )
        );
    }

    public static Hash256 createSimpleProposal(SmartContract contract, Account proposer,
            String offchainUri) throws Throwable {

        return contract.invokeFunction(CREATE, hash160(proposer),
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        "balanceOf",
                                        array(new Hash160(defaultAccountScriptHash())),
                                        CallFlags.ALL.getValue()
                                )
                        ),
                        string(offchainUri),
                        integer(-1))
                .signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
    }


    public static int createAndEndorseProposal(GrantSharesGovContract gov, Neow3j neow3j, Account proposer,
            Account endorser, ContractParameter intents, String offchainUri) throws Throwable {
        TransactionBuilder b = gov.invokeFunction(CREATE, hash160(proposer), intents, string(offchainUri),
                integer(-1));
        return sendAndEndorseProposal(gov, neow3j, proposer, endorser, b);
    }

    public static int createAndEndorseProposal(GrantSharesGovContract gov, Neow3j neow3j, Account proposer,
            Account endorser, ContractParameter intents, String offchainUri, int acceptanceRate, int quorum)
            throws Throwable {
        TransactionBuilder b = gov.invokeFunction(CREATE, hash160(proposer), intents, string(offchainUri),
                integer(-1), integer(acceptanceRate), integer(quorum));

        return sendAndEndorseProposal(gov, neow3j, proposer, endorser, b);
    }

    private static int sendAndEndorseProposal(GrantSharesGovContract gov, Neow3j neow3j, Account proposer,
            Account endorser, TransactionBuilder b) throws Throwable {
        Hash256 tx = b.signers(AccountSigner.calledByEntry(proposer))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        int id = neow3j.getApplicationLog(tx).send().getApplicationLog()
                .getExecutions().get(0).getStack().get(0).getInteger().intValue();

        // 2. endorse proposal
        tx = gov.invokeFunction(ENDORSE, integer(id), hash160(endorser))
                .signers(AccountSigner.calledByEntry(endorser))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        return id;
    }

    public static void voteForProposal(GrantSharesGovContract gov, Neow3j neow3j, int id,
            Account endorserAndVoter) throws Throwable {
        Hash256 tx = gov.invokeFunction(VOTE, integer(id), integer(1), hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    public static void voteForProposal(GrantSharesGovContract gov, Neow3j neow3j, int id, int vote,
            Account endorserAndVoter) throws Throwable {
        Hash256 tx = gov.invokeFunction(VOTE, integer(id), integer(vote),
                        hash160(endorserAndVoter))
                .signers(AccountSigner.calledByEntry(endorserAndVoter))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
    }

    public static Account createMultiSigAccount(int threshold, Account... accounts) {
        List<ECKeyPair.ECPublicKey> pubKeys = Arrays.stream(accounts)
                .map(a -> a.getECKeyPair().getPublicKey())
                .collect(Collectors.toList());
        return Account.createMultiSigAccount(pubKeys, threshold);
    }
}
