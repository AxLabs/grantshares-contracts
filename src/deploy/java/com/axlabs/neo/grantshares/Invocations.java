package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import static com.axlabs.neo.grantshares.Config.getGrantSharesGovHash;
import static com.axlabs.neo.grantshares.Config.getGrantSharesTreasuryHash;
import static com.axlabs.neo.grantshares.Config.getIntProperty;
import static com.axlabs.neo.grantshares.Config.getNeow3j;
import static com.axlabs.neo.grantshares.DeployConfig.EXPIRATION_LENGTH_KEY;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.map;

public class Invocations {

    static {
        Config.setProfile("prod");
    }

    public static void main(String[] args) throws Throwable {
        GrantSharesGovContract gov = new GrantSharesGovContract(getGrantSharesGovHash(), getNeow3j());
        GrantSharesTreasuryContract tre = new GrantSharesTreasuryContract(getGrantSharesTreasuryHash(), getNeow3j());
        Account a = Config.getDeployAccount();

//        createProposal(tre, gov, a);
//        endorseProposal(gov, a, 0);
//        vote(gov, a, 0);
//        gov.printProposalTimes(0);
    }

    private static void vote(GrantSharesGovContract gov, Account a, int id) throws Throwable {
        Hash256 txHash = gov.vote(id, 1, a.getScriptHash()).signers(AccountSigner.calledByEntry(a)).sign().send()
                .getSendRawTransaction().getHash();
        System.out.println(txHash);
    }

    private static void endorseProposal(GrantSharesGovContract gov, Account a, int id) throws Throwable {
        Hash256 txHash = gov.endorseProposal(id, a.getScriptHash()).signers(AccountSigner.calledByEntry(a)).sign()
                .send().getSendRawTransaction().getHash();
        System.out.println(txHash.toString());
    }

    static void createProposal(GrantSharesTreasuryContract treasury, GrantSharesGovContract gov, Account a) throws Throwable {
        IntentParam intent = IntentParam.releaseTokenProposal(treasury.getScriptHash(), NeoToken.SCRIPT_HASH,
                a.getScriptHash(), BigInteger.ONE);
        NeoApplicationLog.Execution exec = signSendAwait(
                gov.createProposal(a.getScriptHash(), "https://github.com/axlabs/grantshares-dev/issues/42", -1,
                        intent), a);
    }

    static NeoApplicationLog.Execution signSendAwait(TransactionBuilder b, Account signer) throws Throwable {
        b = b.signers(AccountSigner.calledByEntry(signer));
        InvocationResult res = b.callInvokeScript().getInvocationResult();
        if (res.hasStateFault()) {
            System.out.println(res.getStack());
            System.out.println("Contract failed with exception: \n" + res.getException() + "\n");
            throw new RuntimeException();
        }
        Hash256 tx = b.sign().send().getSendRawTransaction().getHash();
        System.out.println("Transaction Hash: " + tx);
        Await.waitUntilTransactionIsExecuted(tx, getNeow3j());
        return getNeow3j().getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0);
    }

    static String generateImportMultisigAddressStringForNeoCli(GrantSharesGovContract gov) throws IOException {
        String concatKeys = gov.getMembers().stream()
                .map(k -> Numeric.cleanHexPrefix(k.getEncodedCompressedHex()))
                .reduce((k1, k2) -> k1 + " " + k2).get();
        int t = gov.calcMembersMultiSigAccountThreshold();
        return "import multisigaddress " + t + " " + concatKeys;
    }
}
