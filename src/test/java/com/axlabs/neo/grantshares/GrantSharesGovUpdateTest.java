package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.FileInputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static io.neow3j.transaction.AccountSigner.calledByEntry;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ContractTest(contracts = {}, blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesGovUpdateTest {

    static final Path TEST_MANIFEST_FILE = Paths.get("src/test/resources/GrantSharesGov.manifest.json");
    static final Path TEST_NEF_FILE = Paths.get("src/test/resources/GrantSharesGov.nef");

    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    static final String EXPIRATION_LENGTH_KEY = "expiration_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String THRESHOLD_KEY = "threshold";

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension();
    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static Account alice;

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount(ALICE);

        NefFile nefFile = NefFile.readFromFile(TEST_NEF_FILE.toFile());
        ContractManifest manifest;
        try (FileInputStream s = new FileInputStream(TEST_MANIFEST_FILE.toFile())) {
            manifest = ObjectMapperFactory.getObjectMapper().readValue(s, ContractManifest.class);
        }
        List<ContractParameter> members = Collections.singletonList(ContractParameter.hash160(alice.getScriptHash()));
        ContractParameter deployConfig = array(
                members,
                array(
                        REVIEW_LENGTH_KEY, 0,
                        VOTING_LENGTH_KEY, 300000,
                        TIMELOCK_LENGTH_KEY, 300000,
                        EXPIRATION_LENGTH_KEY, 2592000000L,
                        MIN_ACCEPTANCE_RATE_KEY, 50,
                        MIN_QUORUM_KEY, 50,
                        THRESHOLD_KEY, 75
                )
        );

        TransactionBuilder builder = new ContractManagement(neow3j)
                .deploy(nefFile, manifest, deployConfig)
                .signers(AccountSigner.none(alice));
        Hash256 txHash = builder.sign().send().getSendRawTransaction().getHash();

        waitUntilTransactionIsExecuted(txHash, neow3j);

        Hash160 contractHash = SmartContract.calcContractHash(
                alice.getScriptHash(), nefFile.getCheckSumAsInteger(), manifest.getName()
        );
        gov = new GrantSharesGovContract(contractHash, neow3j);
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
