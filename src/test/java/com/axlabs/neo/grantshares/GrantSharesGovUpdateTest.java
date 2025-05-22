package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import com.axlabs.neo.grantshares.util.ProposalPaginatedStruct;
import com.axlabs.neo.grantshares.util.ProposalStruct;
import com.axlabs.neo.grantshares.util.TestHelper;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.NefFile;
import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.Base64;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.ContractManifest;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.types.StackItemType;
import io.neow3j.wallet.Account;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.CREATE;
import static com.axlabs.neo.grantshares.util.TestHelper.GovernanceMethods.GET_MEMBERS_COUNT;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.Members.CHARLIE;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ContractTest(contracts = {}, blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class GrantSharesGovUpdateTest {

    static final Path TEST_MANIFEST_FILE = Paths.get("src/test/resources/GrantSharesGov.manifest.json");
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    static final String EXPIRATION_LENGTH_KEY = "expiration_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String THRESHOLD_KEY = "threshold";

    static final int VOTING_LENGTH = 10000;
    static final int TIMELOCK_LENGTH = 10000;

    @RegisterExtension
    private static final ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static Account alice;
    private static Account charlie;

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        alice = ext.getAccount(ALICE);
        charlie = ext.getAccount(CHARLIE);

        NefFile nefFile = getNefFile();
        ContractManifest manifest = getContractManifest();

        List<ContractParameter> members = asList(publicKey(alice.getECKeyPair().getPublicKey()));
        ContractParameter deployConfig = array(
                members,
                array(
                        REVIEW_LENGTH_KEY, 0,
                        VOTING_LENGTH_KEY, VOTING_LENGTH,
                        TIMELOCK_LENGTH_KEY, TIMELOCK_LENGTH,
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
        // Verify that the deployed contract's nef checksum is exactly the same as the contract we'd like to update.
        assertThat(neow3j.getContractState(gov.getScriptHash()).send().getContractState().getNef().getChecksum(),
                is(1180315207L)
        );

        // Create two proposals to test the migration logic:
        // First proposal - will be endorsed before update
        ContractParameter intents1 = array(array(gov.getScriptHash(), "changeParam",
                array(string("min_accept_rate"), integer(60)), CallFlags.ALL.getValue()
        ));
        int id = TestHelper.createAndEndorseProposal(gov, neow3j, charlie, alice, intents1, "proposal1");
        // Get old style endorser to validate
        List<StackItem> list = gov.callInvokeFunction("getProposal", asList(integer(id))).getInvocationResult()
                .getStack().get(0).getList();

        StackItem stackItem = list.get(5);
        assertThat(stackItem.getType(), is(StackItemType.BYTE_STRING));
        assertThat(stackItem.getHexString(), is(reverseByteArrayToHexString(alice.getScriptHash().toArray())));

        // Second proposal - will remain unendorsed until after update
        ContractParameter intents2 = array(array(gov.getScriptHash(), "changeParam",
                array(string("min_quorum"), integer(55)), CallFlags.ALL.getValue()
        ));
        Hash256 tx = gov.invokeFunction(CREATE, hash160(charlie), intents2, string("proposal2"), integer(-1))
                .signers(AccountSigner.calledByEntry(charlie))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);
    }

    public static String reverseByteArrayToHexString(byte[] bytes) {
        for (int i = 0; i < bytes.length / 2; i++) {
            byte temp = bytes[i];
            bytes[i] = bytes[bytes.length - 1 - i];
            bytes[bytes.length - 1 - i] = temp;
        }

        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }

        return result.toString();
    }

    private static ContractManifest getContractManifest() throws IOException {
        ContractManifest manifest;
        try (FileInputStream s = new FileInputStream(TEST_MANIFEST_FILE.toFile())) {
            manifest = ObjectMapperFactory.getObjectMapper().readValue(s, ContractManifest.class);
        }
        return manifest;
    }

    private static @NotNull NefFile getNefFile() {
        String compiler = "neow3j-3.17.1";
        String sourceUrl =
                "https://github.com/AxLabs/grantshares-contracts/blob/main/src/main/java/com/axlabs/neo/grantshares" +
                        "/GrantSharesGov.java";
        NefFile.MethodToken methodToken1 = new NefFile.MethodToken(
                new Hash160("0xacce6fd80d44e1796aa0c2c625e9e4e0ce39efc0"),
                "deserialize",
                1, true, CallFlags.ALL
        );
        NefFile.MethodToken methodToken2 = new NefFile.MethodToken(
                new Hash160("0xacce6fd80d44e1796aa0c2c625e9e4e0ce39efc0"),
                "serialize",
                1, true, CallFlags.ALL
        );
        NefFile.MethodToken methodToken3 = new NefFile.MethodToken(
                new Hash160("0xfffdc93764dbaddd97c48f252a53ea4643faa3fd"),
                "update",
                3, false, CallFlags.ALL
        );
        String nefBase64 = "VwkCeSXGAAAAeHBoEc5xEHJqacovMQAAAGlqznNpahGeznRrbFA1tg0AAFhrbBJNEc5Ri1EQzkHmPxiEahKeciPR" +
                "////aBDOcmrKOWpza8p0EHVtbC83AAAAa23Odm5KStkoUNkwrFDKACGzqzlZbkHPmYcCbhJNEc5Ri1EQzkHmPxiEbZx1I8z" +
                "///9aDAkjX21lbWJlcnNqylNB5j8YhFoMBnBhdXNlZBBTQeY/GIRaDAsjX3Byb3Bvc2FscxBTQeY/GIQj" +
                "+wAAAFsSUEoRzlAQzkHfMLiacGhBnAjtnCepAAAAaEHzVL8dcWkQzkrYJgM42yFyaRHONwAAc8J0axTOdW3KdhB3B28Hbi8uAAAAbW8HzncIbBTDSm8IEM5vCBHObwgSzh8VVTU/EQAAz28HnHcHI9T///8Ww0prEM5rEc5rEs5rE85saxXOF1U1MhEAAHVbam03AQASTRHOUYtREM5B5j8YhGoRwAwQUHJvcG9zYWxNaWdyYXRlZEGVAW9hI1b///94cRByamnKLzEAAABpas5zaWoRns50a2xQNTsMAABYa2wSTRHOUYtREM5B5j8YhGoSnnIj0f///0BXAAFYeEsRzlCLUBDOQZJd6DFAVwMAWBJQShHOUBDOQd8wuJpwyHFoQZwI7ZwnGQAAAGhB81S/HXJpahDOahHO0CPm////aUBXAwEAEcNKNZMQAABwaHgQUNBbeEsRzlCLUBDOQZJd6DFxadglOQAAAGk3AAByaGoQzhFQ0GhqEc4SUNBoahLOE1DQaGoTzhRQ0GhqFM4bUNBoahXOHFDQIwcAAAALQFx4SxHOUItQEM5Bkl3oMXFp2CU0AAAAaTcAAHJoahHOFVDQaGoSzhZQ0GhqE84XUNBoahTOGFDQaGoVzhlQ0GhqFs4aUNBdeEsRzlCLUBDOQZJd6DFxadglJgAAAGk3AAByaGoQzh1Q0GhqEc4eUNBoahLOH1DQaGoTziBQ0GhAVwIAWRRQShHOUBDOQd8wuJpwwnFoQZwI7ZwnEgAAAGloQfNUvx3PI+3///9pQEH2tGviDAkjX21lbWJlcnNQQZJd6DHbIUBB9rRr4gwLI19wcm9wb3NhbHNQQZJd6DHbIUBXBAJ4EC8+AAAADDZbR3JhbnRTaGFyZXNHb3YuZ2V0UHJvcG9zYWxzXSBQYWdlIG51bWJlciB3YXMgbmVnYXRpdmU6eRAtRgAAAAw+W0dyYW50U2hhcmVzR292LmdldFByb3Bvc2Fsc10gUGFnZSBudW1iZXIgd2FzIG5lZ2F0aXZlIG9yIHplcm86Qfa0a+IMCyNfcHJvcG9zYWxzUEGSXegx2yFwaHh5UzWzDgAAccJyaRDOc2tpEc4vFQAAAGprNf79///Pa5xzI+z///8Tw0p4aRLOalQ1Bw8AAEBB9rRr4gwGcGF1c2VkUEGSXegx2yBANREAAAA1pf7//1BBajPpCUBXBABB9rRr4gwJI19tZW1iZXJzUEGSXegx2yFwWAwJdGhyZXNob2xkSxHOUItQEM5Bkl3oMdshcWhpoHJqAGShc2oAZKInCAAAAGucc2slTwAAAAxHW0dyYW50U2hhcmVzR292LmNhbGNNZW1iZXJzTXVsdGlTaWdBY2NvdW50VGhyZXNob2xkXSBUaHJlc2hvbGQgd2FzIHplcm86a0BXAAR4eXp7WAwPbWluX2FjY2VwdF9yYXRlSxHOUItQEM5Bkl3oMdshWAwKbWluX3F1b3J1bUsRzlCLUBDOQZJd6DHbIRZVNQYAAABAVwIGeEH4J+yMJSsAAAAMDk5vdCBhdXRob3Jpc2VkDA5jcmVhdGVQcm9wb3NhbFA16gwAAHxYDA9taW5fYWNjZXB0X3JhdGVLEc5Qi1AQzkGSXegx2yExDQAAAHwAZDM0AAAADBdJbnZhbGlkIGFjY2VwdGFuY2UgcmF0ZQwOY3JlYXRlUHJvcG9zYWxQNYwMAAB9WAwKbWluX3F1b3J1bUsRzlCLUBDOQZJd6DHbITENAAAAfQBkMysAAAAMDkludmFsaWQgcXVvcnVtDA5jcmVhdGVQcm9wb3NhbFA1PAwAAHsQMU8AAABce0sRzlCLUBDOQZJd6DHYJzoAAAAMHUxpbmtlZCBwcm9wb3NhbCBkb2Vzbid0IGV4aXN0DA5jcmVhdGVQcm9wb3NhbFA16wsAAHk1BQEAACUsAAAADA9JbnZhbGlkIGludGVudHMMDmNyZWF0ZVByb3Bvc2FsUDW5CwAAQfa0a+IMCyNfcHJvcG9zYWxzUEGSXegx2yFwWAwOZXhwaXJhdGlvbl9sZW5LEc5Qi1AQzkGSXegx2yFBt8OIA55xXGgXw0poaVM1UwwAADcBABJNEc5Ri1EQzkHmPxiEW2gWw0p4e3x9eXoXVTV1CwAANwEAEk0RzlGLURDOQeY/GIRdaBTDSjU8DAAANwEAEk0RzlGLURDOQeY/GIRaDAsjX3Byb3Bvc2Fsc2gRnlNB5j8YhGh4fH1UFMAMD1Byb3Bvc2FsQ3JlYXRlZEGVAW9haEBXBAF4cGjKcRByamkviAAAAGhqznNrEM5KStkoUNkwrFDKABSzqydkAAAAaxDODBQAAAAAAAAAAAAAAAAAAAAAAAAAAJclRQAAAGsQzgwU/aP6Q0bqUyolj8SX3a3bZDfJ/f+XJSYAAABrEc7YJR0AAABrEc4MAJclEgAAAGsTzhEBAAG7JQcAAAAQQGqcciN7////EUBXAgI1AwoAAFl5SxHOUItQEM5Bkl3oMdglEAAAAHlB+CfsjCUsAAAADA5Ob3QgYXV0aG9yaXNlZAwPZW5kb3JzZVByb3Bvc2FsUDUACgAAXHhLEc5Qi1AQzkGSXegxcGjYJzQAAAAMFlByb3Bvc2FsIGRvZXNuJ3QgZXhpc3QMD2VuZG9yc2VQcm9wb3NhbFA1ugkAAGg3AABxaRXOQbfDiAMtLgAAAAwQUHJvcG9zYWwgZXhwaXJlZAwPZW5kb3JzZVByb3Bvc2FsUDV/CQAAaRHO2CU3AAAADBlQcm9wb3NhbCBhbHJlYWR5IGVuZG9yc2VkDA9lbmRvcnNlUHJvcG9zYWxQNUQJAABpeRFQ0GlBt8OIA1gMCnJldmlld19sZW5LEc5Qi1AQzkGSXegx2yGeElDQaWkSzlgMCnZvdGluZ19sZW5LEc5Qi1AQzkGSXegx2yGeE1DQaWkTzlgMDHRpbWVsb2NrX2xlbksRzlCLUBDOQZJd6DHbIZ4UUNBpaRTOWAwOZXhwaXJhdGlvbl9sZW5LEc5Qi1AQzkGSXegx2yGeFVDQXHhpNwEAEk0RzlGLURDOQeY/GIR4eVASwAwQUHJvcG9zYWxFbmRvcnNlZEGVAW9hQFcEAzUqCAAAeQ8xDAAAAHkRMx8AAAAMDEludmFsaWQgdm90ZQwEdm90ZVA1RggAAFl6SxHOUItQEM5Bkl3oMdglEAAAAHpB+CfsjCUhAAAADA5Ob3QgYXV0aG9yaXNlZAwEdm90ZVA1CggAAFx4SxHOUItQEM5Bkl3oMXBo2CcpAAAADBZQcm9wb3NhbCBkb2Vzbid0IGV4aXN0DAR2b3RlUDXPBwAAaDcAAHFBt8OIA3JpEc7YJRcAAABqaRLOMQ4AAABqaRPOMSYAAAAME1Byb3Bvc2FsIG5vdCBhY3RpdmUMBHZvdGVQNYgHAABdeEsRzlCLUBDOQZJd6DE3AABzaxPOessnMQAAAAweQWxyZWFkeSB2b3RlZCBvbiB0aGlzIHByb3Bvc2FsDAR2b3RlUDU/BwAAaxPOennQeRAvEwAAAGtKEc4RnhFQ0CMjAAAAeRAzEwAAAGtKEM4RnhBQ0CMOAAAAa0oSzhGeElDQXXhrNwEAEk0RzlGLURDOQeY/GIR4enlTE8AMBVZvdGVkQZUBb2FAVwkBNZMGAABceEsRzlCLUBDOQZJd6DFwaNgnLAAAAAwWUHJvcG9zYWwgZG9lc24ndCBleGlzdAwHZXhlY3V0ZVA1mQYAAGg3AABxaRHO2CUSAAAAQbfDiANpFM4vNQAAAAwfUHJvcG9zYWwgbm90IGluIGV4ZWN1dGlvbiBwaGFzZQwHZXhlY3V0ZVA1TgYAAGkWzicvAAAADBlQcm9wb3NhbCBhbHJlYWR5IGV4ZWN1dGVkDAdleGVjdXRlUDUcBgAAaRXOQbfDiAMtJgAAAAwQUHJvcG9zYWwgZXhwaXJlZAwHZXhlY3V0ZVA17gUAAFt4SxHOUItQEM5Bkl3oMTcAAHJdeEsRzlCLUBDOQZJd6DE3AABzaxDOaxLOnmsRzp50bABkoEH2tGviDAkjX21lbWJlcnNQQZJd6DHbIaFqE84vKAAAAAwSUXVvcnVtIG5vdCByZWFjaGVkDAdleGVjdXRlUDV0BQAAaxDOaxHOnnVtJxUAAABrEM4AZKBtoWoSzi0nAAAADBFQcm9wb3NhbCByZWplY3RlZAwHZXhlY3V0ZVA1NAUAAGkRFlDQahTOysN2XHhpNwEAEk0RzlGLURDOQeY/GIQQdwdvB2oUzsovMQAAAGoUzm8HzncIbm8HbwgQzm8IEc5vCBPObwgSzlRBYn1bUtBvB5x3ByPO////eBHADBBQcm9wb3NhbEV4ZWN1dGVkQZUBb2FuQFcAAjVzBAAANRIEAAB4eVA1MwAAAFh4eRJNEc5Ri1EQzkHmPxiEeHlQEsAMEFBhcmFtZXRlckNoYW5nZWRBlQFvYUBXAgJ4cGgMCnJldmlld19sZW6XJYYAAABoDAp2b3RpbmdfbGVulyVzAAAAaAwMdGltZWxvY2tfbGVulyVeAAAAaAwOZXhwaXJhdGlvbl9sZW6XJUcAAABoDA9taW5fYWNjZXB0X3JhdGWXJWcAAABoDAptaW5fcXVvcnVtlyVUAAAAaAwJdGhyZXNob2xklyWCAAAAI70AAAB5EC/cAAAADBdJbnZhbGlkIHBhcmFtZXRlciB2YWx1ZQwLY2hhbmdlUGFyYW1QNbADAAAjqwAAAHkQMQ0AAAB5AGQznAAAAAwXSW52YWxpZCBwYXJhbWV0ZXIgdmFsdWUMC2NoYW5nZVBhcmFtUDVwAwAAI2sAAAB5EDMNAAAAeQBkM1wAAAAMF0ludmFsaWQgcGFyYW1ldGVyIHZhbHVlDAtjaGFuZ2VQYXJhbVA1MAMAACMrAAAADBFVbmtub3duIHBhcmFtZXRlcgwLY2hhbmdlUGFyYW1QNQUDAABAVwEBNbgCAAA1VwIAAHhBz5mHAnBZaEsRzlCLUBDOQZJd6DHYJSgAAAAMEEFscmVhZHkgYSBtZW1iZXIMCWFkZE1lbWJlclA1uAIAAFloeBJNEc5Ri1EQzkHmPxiEWgwJI19tZW1iZXJzQfa0a+IMCSNfbWVtYmVyc1BBkl3oMdshEZ5TQeY/GIRoEcAMC01lbWJlckFkZGVkQZUBb2FAVwEBNRkCAAA1uAEAAHhBz5mHAnBZaEsRzlCLUBDOQZJd6DHYJycAAAAMDE5vdCBhIG1lbWJlcgwMcmVtb3ZlTWVtYmVyUDUaAgAAWWhLEc5Qi1AQzkEvWMXtWgwJI19tZW1iZXJzQfa0a+IMCSNfbWVtYmVyc1BBkl3oMdshEZ9TQeY/GIRoEcAMDU1lbWJlclJlbW92ZWRBlQFvYUBXAAM1ewEAADUaAQAAEMAMEFVwZGF0aW5nQ29udHJhY3RBlQFvYXh5elM3AgBAVwIAC3A8FAAAAAAAAAA1cfP//3AjFAAAAHFpDAVwYXVzZVA1cQEAAGhB+CfsjCUiAAAADA5Ob3QgYXV0aG9yaXplZAwFcGF1c2VQNUkBAABaDAZwYXVzZWQRU0HmPxiEEMAMDkNvbnRyYWN0UGF1c2VkQZUBb2FAVwIAC3A8FAAAAAAAAAA1+fL//3AjFAAAAHFpDAVwYXVzZVA1+QAAAGhB+CfsjCUkAAAADA5Ob3QgYXV0aG9yaXplZAwHdW5wYXVzZVA1zwAAAFoMBnBhdXNlZBBTQeY/GIQQwAwQQ29udHJhY3RVbnBhdXNlZEGVAW9hQEE5U248Qdv+qHSXJVAAAAAMK01ldGhvZCBvbmx5IGNhbGxhYmxlIGJ5IHRoZSBjb250cmFjdCBpdHNlbGYMFmFib3J0SWZDYWxsZXJJc05vdFNlbGZQNUoAAABAQfa0a+IMBnBhdXNlZFBBkl3oMdsgJy4AAAAMEkNvbnRyYWN0IGlzIHBhdXNlZAwNYWJvcnRJZlBhdXNlZFA1BgAAAEBXAAJ4eVASwAwFRXJyb3JBlQFvYThAVwAFeHkQUNB4ehFQ0Hh7ElDQeHwTUNBAVwAHeHkQUNB4ehFQ0Hh7ElDQeHwTUNB4fRRQ0Hh+FVDQQFcAAUBXAwN4ei8MAAAAEXAjHAAAAHh6oiUOAAAAeHqhcCMLAAAAeHqhEZ5weWgxNQAAAAwtW1BhZ2luYXRvci5jYWxjUGFnaW5hdGlvbl0gUGFnZSBvdXQgb2YgYm91bmRzOnp5oHFpep5yaXqeeDMHAAAAeHITxCFKEGnQShFq0EoSaNBAVwAEeHkQUNB4ehFQ0Hh7ElDQQFcAA3h5EFDQeAsRUNB4EBJQ0HgQE1DQeBAUUNB4ehVQ0HgQFlDQQFcAAXgQEFDQeBARUNB4EBJQ0HjIE1DQQFYSQZv2Z85iWhFQEsBkWhJQEsBjWhNQEsBlWhRQEsBgWhVQEsBhQA==";
        byte[] nefBytes = Base64.decode(nefBase64);
        return new NefFile(compiler, sourceUrl, asList(methodToken1, methodToken2, methodToken3), nefBytes);
    }

    @Test
    @Order(1)
    public void update_grant_shares_gov_contract() throws Throwable {
        // Compile the new version of the contract
        CompilationUnit res = new Compiler().compile(GrantSharesGov.class.getCanonicalName());

        IntentParam intent = IntentParam.updateContractProposal(
                gov.getScriptHash(), res.getNefFile(), res.getManifest()
        );
        // Create and endorse proposal to update the GrantSharesGov contract
        int id = TestHelper.createAndEndorseProposal(gov, neow3j, charlie, alice, array(intent), "updateContract");

        TestHelper.voteForProposal(gov, neow3j, id, alice);
        ext.fastForwardOneBlock(VOTING_LENGTH + TIMELOCK_LENGTH);
        Hash256 tx = gov.execute(id)
                .signers(AccountSigner.none(charlie)).sign().send().getSendRawTransaction().getHash();
        waitUntilTransactionIsExecuted(tx, neow3j);

        // Check if update was successful
        NeoApplicationLog.Execution execution = neow3j.getApplicationLog(tx)
                .send()
                .getApplicationLog()
                .getFirstExecution();

        assertThat(execution.getState(), is(NeoVMStateType.HALT));
        assertThat(execution.getNotifications().size(), is(3));
        assertThat(execution.getNotifications().get(0).getEventName(), is("UpdatingContract"));

        // Verify the contract is still functional by calling a method
        GrantSharesGovContract updatedGov = new GrantSharesGovContract(gov.getScriptHash(), neow3j);
        assertFalse(updatedGov.isPaused());

        // Get the total number of members to calculate expected quorum votes
        int memberCount = updatedGov.callInvokeFunction(GET_MEMBERS_COUNT)
                .getInvocationResult()
                .getStack()
                .get(0)
                .getInteger()
                .intValue();

        // Get all proposals to check their migration status
        ProposalPaginatedStruct page = updatedGov.getProposals(0, 10);

        // Check the first proposal that was endorsed before update
        ProposalStruct proposal1 = page.items.get(0);
        int expectedQuorumVotes = (memberCount * proposal1.quorum + 99) / 100; // Round up
        assertThat(proposal1.quorumVotes, is(expectedQuorumVotes));
        assertThat(proposal1.endorser, is(not(nullValue())));

        // Check the second proposal that was not endorsed before update
        ProposalStruct proposal2 = page.items.get(1);
        assertThat(proposal2.quorumVotes, is(0));
        assertThat(proposal2.endorser, is(nullValue()));

        // Now endorse the second proposal and verify its quorumVotes gets set correctly
        Hash256 endorseTx = updatedGov.endorseProposal(proposal2.id, alice.getScriptHash())
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(endorseTx, neow3j);

        // Check that the quorumVotes was set correctly after endorsement
        proposal2 = updatedGov.getProposal(proposal2.id);
        expectedQuorumVotes = (memberCount * proposal2.quorum + 99) / 100; // Round up
        assertThat(proposal2.quorumVotes, is(expectedQuorumVotes));
        assertThat(proposal2.endorser, is(alice.getScriptHash()));
    }
}
