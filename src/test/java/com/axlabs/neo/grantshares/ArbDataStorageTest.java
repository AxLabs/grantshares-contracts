package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.StorageContract;
import com.axlabs.neo.grantshares.util.TestHelper;
import com.axlabs.neo.grantshares.util.contracts.Storage;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.script.InteropService;
import io.neow3j.script.ScriptBuilder;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.CallFlags;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.ArrayUtils;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.math.BigInteger;

import static com.axlabs.neo.grantshares.util.NetworkSettingsHelper.updateNetworkSettings;
import static com.axlabs.neo.grantshares.util.RequestForFunds.buildIntentsBytes;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;
import static io.neow3j.utils.Await.waitUntilTransactionIsExecuted;
import static java.util.Arrays.asList;

@ContractTest(
        contracts = {Storage.class},
        blockTime = 1, configFile = "default.neo-express", batchFile = "setup.batch")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ArbDataStorageTest {

    @RegisterExtension
    static ContractTestExtension ext = new ContractTestExtension();

    @BeforeAll
    public static void setup() throws Throwable {
        Neow3j neow3j = ext.getNeow3j();

        Account alice = ext.getAccount(TestHelper.Members.ALICE);
        Account committee = Account.createMultiSigAccount(asList(alice.getECKeyPair().getPublicKey()), 1);

        updateNetworkSettings(neow3j, committee, alice);
    }

    public static byte[] buildCreateProposalScript(Hash160 gov, Account proposer) {
        ScriptBuilder b1 = new ScriptBuilder();
        long nrParams = 4L; // proposer, intents, offchainUri, linkedProposal (reversed order of pushing params)
        b1.pushParam(integer(10));
        b1.pushParam(string("https://github.com/AxLabs/grantshares-test/issues/95"));
        byte[] startBytes = b1.toArray();

        ScriptBuilder b2 = new ScriptBuilder();
        b2.pushParam(hash160(proposer));
        b2.pushInteger(nrParams);
        b2.pack();

        b2.pushParam(integer(CallFlags.ALL.getValue()));
        b2.pushData("createProposal");
        b2.pushData(gov.toLittleEndianArray());
        b2.sysCall(InteropService.SYSTEM_CONTRACT_CALL);
        byte[] endBytes = b2.toArray();

        byte[] intentBytes = buildIntentsBytes(
                new Hash160("ef4073a0f2b305a38ec4050e4d3d28bc40ea63f5"), // token
                new Hash160("0xc8dc114f3986579b8f318a484ff0e5f97d120fab"), // treasury
                new Hash160("0x8346705e69ecc085f2216b4836659686dbab59ee"), // bridgeAdapter
                new Hash160("0xEBE5BEcFF0D8BEf442ced1eBb042Eb7E9652b98B"), // recipient
                new BigInteger("100"), // amount
                new BigInteger("10") // bridgeFee
        );
        return ArrayUtils.concatenate(startBytes, intentBytes, endBytes);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }

    private byte[] buildMinimalFunctionCallBytes(Hash160 contract) {
        ScriptBuilder b = new ScriptBuilder();

        b.pushParam(integer(CallFlags.ALL.getValue()));
        b.pushParam(string("m"));
        b.pushParam(hash160(contract));
        b.sysCall(InteropService.SYSTEM_CONTRACT_CALL);

        return b.toArray();
    }

    // Matches the Flamingo `swap`
    // See - https://explorer.onegate.space/contractinfo/0x59aa80468a120fe79aa5601de07746275c9ed76a
    private byte[] buildAverageSwapContractCallBytes(
            Hash160 contract,
            int amount0,
            int amount1,
            Hash160 to,
            byte[] data
    ) {
        ScriptBuilder b = new ScriptBuilder();

        // Push parameters in reverse order
        b.pushData(data);
        b.pushParam(hash160(to));
        b.pushParam(integer(amount1));
        b.pushParam(integer(amount0));
        b.pushInteger(4); // number of parameters
        b.pack();

        // Push method call parameters
        b.pushParam(integer(CallFlags.ALL.getValue()));
        b.pushParam(string("swap"));
        b.pushParam(hash160(contract));
        b.sysCall(InteropService.SYSTEM_CONTRACT_CALL);

        return b.toArray();
    }

    @Test
    public void testStorageCostsForDifferentSizes() throws Throwable {
        Hash160 dummyContract = new Hash160("ef4073a0f2b305a38ec4050e4d3d28bc40ea63f5");
        Account alice = ext.getAccount(TestHelper.Members.ALICE);
        StorageContract storage = new StorageContract(ext.getDeployedContract(Storage.class).getScriptHash(),
                ext.getNeow3j()
        );

        // Generate three different sized byte arrays
        byte[] smallArray = buildMinimalFunctionCallBytes(dummyContract);
        byte[] mediumArray = buildAverageSwapContractCallBytes(
                dummyContract, 1000, 2000, dummyContract, new byte[]{1, 2, 3}
        );
        byte[] largeArray = buildCreateProposalScript(dummyContract, alice);

        // Print hex values
        System.out.println("Small array size: " + smallArray.length + " bytes");
        System.out.println("Small array hex: " + bytesToHex(smallArray));
        System.out.println("Medium array size: " + mediumArray.length + " bytes");
        System.out.println("Medium array hex: " + bytesToHex(mediumArray));
        System.out.println("Large array size: " + largeArray.length + " bytes");
        System.out.println("Large array hex: " + bytesToHex(largeArray));

        // Measure initial storage costs using store()
        System.out.println("\nMeasuring initial storage costs:");
        Hash256 tx1 = storage.store(smallArray)
                .signers(AccountSigner.none(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx1, ext.getNeow3j());
        NeoApplicationLog.Execution exec1 = ext.getNeow3j().getApplicationLog(tx1)
                .send().getApplicationLog().getFirstExecution();

        Hash256 tx2 = storage.store(mediumArray)
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx2, ext.getNeow3j());
        NeoApplicationLog.Execution exec2 = ext.getNeow3j().getApplicationLog(tx2)
                .send().getApplicationLog().getFirstExecution();

        Hash256 tx3 = storage.store(largeArray)
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx3, ext.getNeow3j());
        NeoApplicationLog.Execution exec3 = ext.getNeow3j().getApplicationLog(tx3)
                .send().getApplicationLog().getFirstExecution();

        // Now test overwriting existing values using storeAtIndex()
        System.out.println("\nMeasuring overwrite storage costs:");

        // Store different values first
        storage.storeAtIndex(10, new byte[]{1, 2, 3})
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send();

        Hash256 tx4 = storage.storeAtIndex(10, smallArray)
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx4, ext.getNeow3j());
        NeoApplicationLog.Execution exec4 = ext.getNeow3j().getApplicationLog(tx4)
                .send().getApplicationLog().getFirstExecution();

        storage.storeAtIndex(11, new byte[]{1, 2, 3})
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send();

        Hash256 tx5 = storage.storeAtIndex(11, mediumArray)
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx5, ext.getNeow3j());
        NeoApplicationLog.Execution exec5 = ext.getNeow3j().getApplicationLog(tx5)
                .send().getApplicationLog().getFirstExecution();

        storage.storeAtIndex(12, new byte[]{1, 2, 3})
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send();

        Hash256 tx6 = storage.storeAtIndex(12, largeArray)
                .signers(AccountSigner.calledByEntry(alice))
                .sign()
                .send()
                .getSendRawTransaction()
                .getHash();
        waitUntilTransactionIsExecuted(tx6, ext.getNeow3j());
        NeoApplicationLog.Execution exec6 = ext.getNeow3j().getApplicationLog(tx6)
                .send().getApplicationLog().getFirstExecution();

        // Print hex values and sizes
        System.out.println("\nSize comparison:");
        System.out.printf("Small array size: %d bytes - storage gas used: %s - overwrite gas used: %s%n",
                smallArray.length,
                exec1.getGasConsumed(),
                exec4.getGasConsumed()
        );

        System.out.printf("Medium array size: %d bytes - storage gas used: %s - overwrite gas used: %s%n",
                mediumArray.length,
                exec2.getGasConsumed(),
                exec5.getGasConsumed()
        );

        System.out.printf("Large array size: %d bytes - storage gas used: %s - overwrite gas used: %s%n",
                largeArray.length,
                exec3.getGasConsumed(),
                exec6.getGasConsumed()
        );
    }
}
