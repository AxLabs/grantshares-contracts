package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.axlabs.neo.grantshares.TestConstants.ALICE;
import static com.axlabs.neo.grantshares.TestConstants.BOB;
import static com.axlabs.neo.grantshares.TestConstants.CHARLIE;
import static com.axlabs.neo.grantshares.TestConstants.HASH_PROPOSAL;
import static com.axlabs.neo.grantshares.TestConstants.MAX_METHOD_LEN;
import static com.axlabs.neo.grantshares.TestConstants.MAX_SERIALIZED_INTENT_PARAM_LEN;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE;
import static com.axlabs.neo.grantshares.TestConstants.MIN_ACCEPTANCE_RATE_KEY;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM;
import static com.axlabs.neo.grantshares.TestConstants.MIN_QUORUM_KEY;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.QUEUED_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.REVIEW_LENGTH_KEY;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH;
import static com.axlabs.neo.grantshares.TestConstants.VOTING_LENGTH_KEY;
import static io.neow3j.test.TestProperties.defaultAccountScriptHash;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class HashProposalTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static SmartContract contract;
    private static Account alice; // Set to be a DAO member.
    private static Account bob;
    private static Account charlie; // Set to be a DAO member.
    private static String defaultProposalHash;
    private static MessageDigest hasher;

    @DeployConfig(GrantSharesGov.class)
    public static void deployConfig(DeployConfiguration config) throws Exception {
        config.setDeployParam(array(
                array(
                        ext.getAccount(ALICE).getScriptHash(),
                        ext.getAccount(CHARLIE).getScriptHash()),
                array(
                        REVIEW_LENGTH_KEY, REVIEW_LENGTH,
                        VOTING_LENGTH_KEY, VOTING_LENGTH,
                        QUEUED_LENGTH_KEY, QUEUED_LENGTH,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM
                )
        ));
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        contract = ext.getDeployedContract(GrantSharesGov.class);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
        hasher = MessageDigest.getInstance("SHA-256");
    }

    @Test
    public void succeed_hashing_proposal() throws Throwable {
        String method = "balanceOf";
        Hash160 param = new Hash160(defaultAccountScriptHash());
        byte[] descriptionHash = hasher.digest("succeed_hashing_proposal".getBytes(UTF_8));

        int size = 20 // target contract
                + MAX_METHOD_LEN
                + MAX_SERIALIZED_INTENT_PARAM_LEN
                + 32; // SHA-256 of the description

        byte[] paddedMethod = new byte[MAX_METHOD_LEN];
        System.arraycopy(method.getBytes(UTF_8), 0, paddedMethod, 0, method.getBytes(UTF_8).length);
        byte[] paddedParam = new byte[MAX_SERIALIZED_INTENT_PARAM_LEN];
        byte[] serializedParam = Numeric.hexStringToByteArray(""
                + "28" // stack item type -> ByteString
                + "14" // length of ByteString
                + Numeric.toHexStringNoPrefix(param.toLittleEndianArray()));
        System.arraycopy(serializedParam, 0, paddedParam, 0, serializedParam.length);

        byte[] concatenatedIntent = ByteBuffer.allocate(size)
                .put(NeoToken.SCRIPT_HASH.toLittleEndianArray())
                .put(paddedMethod)
                .put(paddedParam)
                .put(descriptionHash)
                .array();

        byte[] expectedIntentHash = hasher.digest(concatenatedIntent);

        InvocationResult res =
                contract.invokeFunction(HASH_PROPOSAL,
                                array(
                                        array(
                                                NeoToken.SCRIPT_HASH,
                                                method,
                                                array(param)
                                        )
                                ),
                                byteArray(descriptionHash))
                        .callInvokeScript().getInvocationResult();

        byte[] intentHash = res.getStack().get(0).getByteArray();
        assertThat(intentHash, is(expectedIntentHash));
    }

    @Test
    public void fail_hashing_proposal_with_oversized_method_name() throws Throwable {
        String method = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Hash160 param = new Hash160(defaultAccountScriptHash());
        byte[] descriptionHash = hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
        String exception = contract.invokeFunction(HASH_PROPOSAL,
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        method,
                                        array(param)
                                )
                        ),
                        byteArray(descriptionHash))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Target method name too long"));
    }

}
