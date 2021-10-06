package com.axlabs.neo.grantshares;

import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3jExpress;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.NeoInvokeFunction;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.List;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.integer;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ContractTest(contractClass = GrantSharesGov.class, blockTime = 1)
public class GrantSharesGovTest {

    // contract methods
    private static final String ADD_PROPOSAL = "addProposal";
    private static final String GET_PROPOSAL = "getProposal";
    private static final String EXECUTE = "execute";
    private static final String CALLBACK = "callback";

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static int propId;

    private final Neow3jExpress neow3j;
    private final SmartContract contract;
    private final Account alice;

    public GrantSharesGovTest(Neow3jExpress neow3j, SmartContract contract) throws IOException {
        this.neow3j = neow3j;
        this.contract = contract;
        this.alice = ext.getAccount("Alice");
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        Account alice = ext.getAccount("Alice");
        ContractParameter proposal = array(Hash160.ZERO, alice.getScriptHash(), 0, 0, CALLBACK);
        Hash256 txHash = ext.getContractUnderTest().invokeFunction(ADD_PROPOSAL, proposal)
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, ext.getNeow3j());

        NeoApplicationLog log = ext.getNeow3j().getApplicationLog(txHash).send().getApplicationLog();
        propId = log.getExecutions().get(0).getStack().get(0).getInteger().intValue();
    }

    @Test
    public void addProposal() throws Throwable {
        NeoInvokeFunction r = contract.callInvokeFunction(GET_PROPOSAL, asList(integer(propId)));
        List<StackItem> list = r.getInvocationResult().getStack().get(0).getList();
        assertThat(list.get(0).getInteger().intValue(), is(propId));
        assertThat(list.get(1).getAddress(), is(alice.getAddress()));
        assertThat(list.get(2).getInteger().intValue(), is(0));
        assertThat(list.get(3).getInteger().intValue(), is(0));
        assertThat(list.get(4).getString(), is(CALLBACK));
    }

    @Test
    public void executeProposal() throws Throwable {
        Hash256 txHash = contract.invokeFunction(EXECUTE, integer(propId))
                .signers(AccountSigner.calledByEntry(alice))
                .sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        NeoApplicationLog log = neow3j.getApplicationLog(txHash).send().getApplicationLog();
        assertTrue(log.getExecutions().get(0).getStack().get(0).getBoolean());
    }

}