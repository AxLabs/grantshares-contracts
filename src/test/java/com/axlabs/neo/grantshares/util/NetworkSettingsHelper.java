package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.PolicyContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.transaction.Transaction;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;

import static io.neow3j.transaction.AccountSigner.calledByEntry;

public class NetworkSettingsHelper {

    // The current network settings on mainnet
    public static final BigInteger networkFeePerByte = new BigInteger("20");
    public static final BigInteger storageFeeFactor = new BigInteger("10000");
    public static final BigInteger executionFeeFactor = new BigInteger("1");

    public static void updateNetworkSettings(Neow3j neow3j, Account committeeMultiSigAcc, Account committeeSignerAcc) throws Throwable {
        setNetworkFeePerByte(neow3j, committeeMultiSigAcc, committeeSignerAcc);
        setStorageFeeFactor(neow3j, committeeMultiSigAcc, committeeSignerAcc);
        setExecutionFeeFactor(neow3j, committeeMultiSigAcc, committeeSignerAcc);
        printNetworkSettings(neow3j);
    }

    private static void printNetworkSettings(Neow3j neow3j) throws IOException {
        PolicyContract policyContract = new PolicyContract(neow3j);
        System.out.println("\n################");
        System.out.println("Network Settings");
        System.out.println("----------------");
        System.out.println("Network fee per byte: " + policyContract.getFeePerByte());
        System.out.println("Storage fee factor:   " + policyContract.getStoragePrice());
        System.out.println("Execution fee factor: " + policyContract.getExecFeeFactor());
        System.out.println("################\n");
    }

    private static void setNetworkFeePerByte(Neow3j neow3j, Account committeeMultiSig, Account committeeSignerAcc) throws Throwable {
        PolicyContract policyContract = new PolicyContract(neow3j);
        Transaction tx = policyContract.setFeePerByte(networkFeePerByte)
                .signers(calledByEntry(committeeMultiSig))
                .getUnsignedTransaction();
        tx.addMultiSigWitness(committeeMultiSig.getVerificationScript(), committeeSignerAcc);
        Hash256 txHash = tx.send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    private static void setStorageFeeFactor(Neow3j neow3j, Account committeeMultiSigAcc, Account committeeSignerAcc) throws Throwable {
        PolicyContract policyContract = new PolicyContract(neow3j);
        Transaction tx = policyContract.setStoragePrice(storageFeeFactor)
                .signers(calledByEntry(committeeMultiSigAcc))
                .getUnsignedTransaction();
        tx.addMultiSigWitness(committeeMultiSigAcc.getVerificationScript(), committeeSignerAcc);
        Hash256 txHash = tx.send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    private static void setExecutionFeeFactor(Neow3j neow3j, Account committeeMultiSigAcc,
            Account committeeSignerAcc) throws Throwable {
        PolicyContract policyContract = new PolicyContract(neow3j);
        Transaction tx = policyContract.setExecFeeFactor(executionFeeFactor)
                .signers(calledByEntry(committeeMultiSigAcc))
                .getUnsignedTransaction();
        tx.addMultiSigWitness(committeeMultiSigAcc.getVerificationScript(), committeeSignerAcc);
        Hash256 txHash = tx.send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

}
