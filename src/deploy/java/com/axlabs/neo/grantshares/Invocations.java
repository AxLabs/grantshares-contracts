package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import io.neow3j.protocol.core.response.InvocationResult;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.utils.Numeric;
import io.neow3j.wallet.Account;

import java.io.IOException;

import static com.axlabs.neo.grantshares.Config.getNeow3j;

public class Invocations {

    public static void main(String[] args) throws Throwable {

        Account account = Account.fromWIF("");

        GrantSharesGovContract gov = new GrantSharesGovContract(Config.getGrantSharesGovHash(), getNeow3j());
        GrantSharesTreasuryContract treasury =
                new GrantSharesTreasuryContract(Config.getGrantSharesTreasuryHash(), getNeow3j());

        System.out.println(treasury.calcFundersMultiSigAddressThreshold());
        System.out.println(treasury.getFundersMultiSigAccount().getAddress());

//        Hash256 hash = neo.transfer(claude, treasury.getScriptHash(), BigInteger.ONE).sign().send()
//                .getSendRawTransaction().getHash();
//        Await.waitUntilTransactionIsExecuted(hash, neow3j);

//        CompilationUnit res = new Compiler().compile(GrantSharesTreasury.class.getCanonicalName());
//        IntentParam intent = IntentParam.updateContractProposal(treasury.getScriptHash(), res.getNefFile(),
//                res.getManifest());
//        NeoApplicationLog.Execution exec = signSendAwait(
//                gov.createProposal(claude.getScriptHash(), "discussionUrl", -1, intent), claude);
//        System.out.println("Proposal ID: " + exec.getStack().get(0).getInteger().intValue());

//        NeoApplicationLog.Execution execution = signSendAwait(gov.endorseProposal(0, claude.getScriptHash()), claude);
//        NeoApplicationLog.Execution execution = signSendAwait(gov.vote(5, 1, claude.getScriptHash()), claude);
//        NeoApplicationLog.Execution execution = signSendAwait(gov.execute(5), claude);

//        gov.printProposalTimes(5);
//        ProposalStruct p = gov.getProposal(5);
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
