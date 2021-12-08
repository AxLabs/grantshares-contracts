package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.utils.Numeric;
import org.junit.Ignore;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

import static com.axlabs.neo.grantshares.TestConstants.ALICE;
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
import static io.neow3j.types.ContractParameter.map;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGov.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class HashProposalTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static SmartContract contract;
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
        contract = ext.getDeployedContract(GrantSharesGov.class);
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
    public void fail_hashing_proposal_with_invalid_target_contract_hash() throws Throwable {
        ContractParameter targetContract = byteArray("00010203040506070809");
        String method = "balanceOf";
        Hash160 param = new Hash160(defaultAccountScriptHash());
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
        String exception = contract.invokeFunction(HASH_PROPOSAL,
                        array(
                                array(
                                        targetContract,
                                        method,
                                        array(param)
                                )
                        ),
                        byteArray(descriptionHash))
                .callInvokeScript().getInvocationResult().getException();
        assertThat(exception, containsString("Invalid target contract hash"));
    }

    @Test
    public void succeed_hashing_proposal_with_largest_possible_method_name() throws Throwable {
        String method =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Hash160 param = new Hash160(defaultAccountScriptHash());
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
        InvocationResult res = contract.invokeFunction(HASH_PROPOSAL,
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        method,
                                        array(param)
                                )
                        ),
                        byteArray(descriptionHash))
                .callInvokeScript().getInvocationResult();

        assertThat(res.getException(), is(nullValue()));
        assertThat(res.getStack().get(0).getByteArray().length, is(32));
    }

    @Test
    public void fail_hashing_proposal_with_oversized_method_name() throws Throwable {
        String method =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Hash160 param = new Hash160(defaultAccountScriptHash());
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
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

    @Test
    public void succeed_hashing_proposal_with_largest_possible_param() throws Throwable {
        String method = "balanceOf";
        byte[] param =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        .getBytes(UTF_8);
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
        InvocationResult res = contract.invokeFunction(HASH_PROPOSAL,
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        method,
                                        array(param)
                                )
                        ),
                        byteArray(descriptionHash))
                .callInvokeScript().getInvocationResult();

        assertThat(res.getException(), is(nullValue()));
        assertThat(res.getStack().get(0).getByteArray().length, is(32));
    }

    @Test
    @Ignore("Fails because neo-express is not uptodate with Neo 3.1.0 yet and therefore can't " +
            "handle the new PACKMAP opcode.")
    public void fail_hashing_proposal_with_oversized_param() throws Throwable {
        String method = "balanceOf";
        String keyString =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String valueString =
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        Map<String, String> map = new HashMap<>();
        map.put(keyString + "0", valueString);
        map.put(keyString + "1", valueString);
        map.put(keyString + "2", valueString);
        map.put(keyString + "4", valueString);
        ContractParameter param = map(map);
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
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
        assertThat(exception, containsString("Intent method parameter too big"));
    }

    @Test
    public void succeed_hashing_proposal_with_many_params() throws Throwable {
        String method = "balanceOf";
        byte[] param = "abcdefghijklmnopqrstuvwxzy".getBytes(UTF_8);
        byte[] descriptionHash =
                hasher.digest("fail_hashing_proposal_with_oversized_method_name".getBytes(UTF_8));
        InvocationResult res = contract.invokeFunction(HASH_PROPOSAL,
                        array(
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        method,
                                        array(param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param)
                                ),
                                array(
                                        NeoToken.SCRIPT_HASH,
                                        method,
                                        array(param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param, param, param, param, param, param, param,
                                                param)
                                )
                        ),
                        byteArray(descriptionHash))
                .callInvokeScript().getInvocationResult();

        assertThat(res.getException(), is(nullValue()));
        assertThat(res.getStack().get(0).getByteArray().length, is(32));
    }

}
