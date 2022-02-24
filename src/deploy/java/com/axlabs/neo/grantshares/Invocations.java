package com.axlabs.neo.grantshares;

import io.neow3j.contract.NeoToken;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;

public class Invocations {

    private static final Neow3j neow3j = Config.getPrivatenet();
    private static final GrantSharesGovContract gov = new GrantSharesGovContract(Config.getGrantSharesGovHash(), neow3j);
    private static final Account alice = Account.fromWIF("a238e47f35cce2894d3aecea2fd77ef2cf52abfa");

    public static void main(String[] args) throws Throwable {
        IntentParam intent = IntentParam.releaseTokenProposal(NeoToken.SCRIPT_HASH, alice.getScriptHash(), 10);
        NeoApplicationLog.Execution exec = signSendAwait(
                gov.createProposal(alice.getScriptHash(), "discussionUrl", -1, intent), alice);
    }

    static NeoApplicationLog.Execution signSendAwait(TransactionBuilder b, Account signer) throws Throwable {
        Hash256 tx = b.signers(AccountSigner.calledByEntry(signer)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);
        return neow3j.getApplicationLog(tx).send().getApplicationLog().getExecutions().get(0);
    }

}
