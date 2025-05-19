package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.response.Notification;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static io.neow3j.transaction.AccountSigner.calledByEntry;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesGovUpdateTest {

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension();
    public static final Path TEST_MANIFEST_FILE = Paths.get("GrantSharesGov.manifest.json");
    public static final Path TEST_NEF_FILE = Paths.get("GrantSharesGov.nef");
    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static Account alice;

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount(ALICE);

        byte[] nefFile = Files.readAllBytes(TEST_NEF_FILE);
        String manifestFile = new String(Files.readAllBytes(TEST_MANIFEST_FILE));

        // Build and send update transaction
        Hash256 tx = gov.updateContract(nefFile, manifestFile, null)
                .signers(calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();

        // Wait for transaction to be processed
        waitUntilTransactionIsExecuted(tx, neow3j);

        // Verify update was successful
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx)
                .send()
                .getApplicationLog()
                .getFirstExecution();

        assertThat(execution.getState(), is(NeoVMStateType.HALT));
        List<Notification> notifications = execution.getNotifications();
        assertThat(notifications, hasSize(1));
        assertThat(notifications.get(0).getEventName(), is("UpdatingContract"));
    }

    @Test
    @Order(1)
    public void update_grant_shares_gov_contract() throws Throwable {
        // Compile the new version of the contract
        CompilationUnit res = new Compiler().compile(GrantSharesGov.class.getCanonicalName());

        // Update the contract
        Hash256 tx = gov.updateContract(
                        res.getNefFile().getScript(),
                        res.getManifest().toString(),
                        ContractParameter.any(null)
                )
                .signers(calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();

        // Wait for transaction execution
        waitUntilTransactionIsExecuted(tx, neow3j);

        // Check if update was successful
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx)
                .send()
                .getApplicationLog()
                .getFirstExecution();

        assertThat(execution.getState(), is(NeoVMStateType.HALT));
        assertThat(execution.getNotifications().size(), is(1));
        assertThat(execution.getNotifications().get(0).getEventName(), is("UpdatingContract"));

        // Verify the contract is still functional by calling a method
        GrantSharesGovContract updatedGov = new GrantSharesGovContract(gov.getScriptHash(), neow3j);
        assertFalse(updatedGov.isPaused());
    }

}
