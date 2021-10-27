package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.devpack.Hash160;
import io.neow3j.protocol.Neow3jExpress;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.hash256;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1)
public class GrantSharesGovTest {

    // contract methods
    private static final String CREATE_PROPOSAL = "createProposal";
    private static final String GET_PROPOSAL = "getProposal";
    private static final String EXECUTE = "execute";
    private static final String HASH_PROPOSAL = "hashProposal";

    // governance parameters
    private static final int REVIEW_LENGTH = 5;
    private static final int VOTING_LENGTH = 5;
    private static final int QUEUED_LENGTH = 5;
    private static final int MIN_ACCEPTANCE_RATE = 50;
    private static final int MIN_QUORUM = 25;

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3jExpress neow3j;
    private static SmartContract contract;
    private static Account alice;
    private static String proposalHash;
    private static Hash256 proposalCreationTx;

    @DeployConfig(GrantSharesGov.class)
    public static ContractParameter config(DeployContext ctx) {
        return array(
                new byte[]{10}, REVIEW_LENGTH,
                new byte[]{11}, VOTING_LENGTH,
                new byte[]{12}, QUEUED_LENGTH,
                new byte[]{13}, MIN_ACCEPTANCE_RATE,
                new byte[]{14}, MIN_QUORUM
        );
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(GrantSharesGov.class);
        alice = ext.getAccount("Alice");

        // Create a proposal that transfers 1 NEO from the DAO to Alice.
        ContractParameter intent = array(NeoToken.SCRIPT_HASH, "transfer",
                array(contract.getScriptHash(), alice.getScriptHash(), 1));

        proposalCreationTx = contract.invokeFunction(CREATE_PROPOSAL,
                        hash160(alice.getScriptHash()),
                        array(intent),
                        string("description of the proposal"))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(proposalCreationTx, neow3j);

        NeoApplicationLog log = neow3j.getApplicationLog(proposalCreationTx).send()
                .getApplicationLog();
        proposalHash = Numeric.reverseHexString(
                log.getExecutions().get(0).getStack().get(0).getHexString());
    }

    @Test
    @DisplayName("Succeed adding a proposal and retrieving it")
    public void create_proposal_success() throws Throwable {
        // Tests if the proposal from the setUp method was successfully added.
        NeoInvokeFunction r =
                contract.callInvokeFunction(GET_PROPOSAL, asList(hash256(proposalHash)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        // proposal hash
        assertThat(Numeric.reverseHexString(list.get(0).getHexString()), is(proposalHash));
        // proposer
        assertThat(list.get(1).getAddress(), is(alice.getAddress()));
        // linked proposal
        assertThat(list.get(2).getValue(), is(nullValue()));
        int n = neow3j.getTransactionHeight(proposalCreationTx).send().getHeight().intValue();
        // review end
        assertThat(list.get(3).getInteger().intValue(), is(n + REVIEW_LENGTH));
        // voting end
        assertThat(list.get(4).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH));
        // voting end
        assertThat(list.get(5).getInteger().intValue(), is(n + REVIEW_LENGTH + VOTING_LENGTH
                + QUEUED_LENGTH));
        // acceptance rate
        assertThat(list.get(6).getInteger().intValue(), is(MIN_ACCEPTANCE_RATE));
        // quorum
        assertThat(list.get(7).getInteger().intValue(), is(MIN_QUORUM));
    }

//    @Test
//    @DisplayName("Succeed executing proposal")
//    public void execute_proposal_success() throws Throwable {
//        Hash256 txHash = contract.invokeFunction(EXECUTE, integer(propId))
//                .signers(AccountSigner.calledByEntry(alice))
//                .sign().send().getSendRawTransaction().getHash();
//        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
//        NeoApplicationLog log = neow3j.getApplicationLog(txHash).send().getApplicationLog();
//        assertTrue(log.getExecutions().get(0).getStack().get(0).getBoolean());
//    }
//
//    @Test
//    @DisplayName("Fail calling contract-exclusive method")
//    public void call_contract_excl_method_fail() throws Throwable {
//        NeoInvokeFunction response = contract.callInvokeFunction(CALLBACK, asList(hash160(alice),
//                integer(propId)), AccountSigner.calledByEntry(alice));
//        assertThat(response.getInvocationResult().getException(),
//                containsString("No authorization!"));
//    }

}