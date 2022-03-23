package com.axlabs.neo.grantshares;

import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.ContractManagement;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.transaction.TransactionBuilder;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.types.NeoVMStateType;
import io.neow3j.utils.Await;

public class Deployment {

    static final Neow3j NEOW3J = Config.getNeow3j();
    static final AccountSigner signer = AccountSigner.none(Config.getDeployAccount());

    // TODO: Don't forget to switch `currentIndex()` to `getTime()` in the governance contract.
    public static void main(String[] args) throws Throwable {
        Hash160 grantSharesGovHash = deployGrantSharesGov();
        deployGrantSharesTreasury(grantSharesGovHash);
    }

    private static Hash160 deployGrantSharesGov() throws Throwable {
        CompilationUnit res = new Compiler().compile(GrantSharesGov.class.getCanonicalName());
        TransactionBuilder builder = new ContractManagement(NEOW3J)
                .deploy(res.getNefFile(), res.getManifest(), DeployConfig.getGovDeployConfig())
                .signers(signer);

        Hash256 txHash = builder.sign().send().getSendRawTransaction().getHash();
        System.out.println("GrantSharesGov Deploy Transaction Hash: " + txHash.toString());
        Await.waitUntilTransactionIsExecuted(txHash, NEOW3J);

        NeoApplicationLog log = NEOW3J.getApplicationLog(txHash).send().getApplicationLog();
        if (log.getExecutions().get(0).getState().equals(NeoVMStateType.FAULT)) {
            throw new Exception("Failed to deploy smart contract. NeoVM " +
                    "error message: " + log.getExecutions().get(0).getException());
        }
        Hash160 contractHash = SmartContract.calcContractHash(signer.getScriptHash(),
                res.getNefFile().getCheckSumAsInteger(), res.getManifest().getName());
        System.out.println("GrantSharesGov Contract Hash: " + contractHash);
        return contractHash;
    }

    private static void deployGrantSharesTreasury(Hash160 grantSharesGovHash) throws Throwable {
        CompilationUnit res = new Compiler().compile(GrantSharesTreasury.class.getCanonicalName());
        TransactionBuilder builder = new ContractManagement(NEOW3J)
                .deploy(res.getNefFile(), res.getManifest(),
                        DeployConfig.getTreasuryDeployConfig(grantSharesGovHash))
                .signers(signer);

        Hash256 txHash = builder.sign().send().getSendRawTransaction().getHash();
        System.out.println("GrantSharesTreasury Deploy Transaction Hash: " + txHash.toString());
        Await.waitUntilTransactionIsExecuted(txHash, NEOW3J);

        NeoApplicationLog log = NEOW3J.getApplicationLog(txHash).send().getApplicationLog();
        if (log.getExecutions().get(0).getState().equals(NeoVMStateType.FAULT)) {
            throw new Exception("Failed to deploy smart contract. NeoVM " +
                    "error message: " + log.getExecutions().get(0).getException());
        }
        Hash160 contractHash = SmartContract.calcContractHash(signer.getScriptHash(),
                res.getNefFile().getCheckSumAsInteger(), res.getManifest().getName());
        System.out.println("GrantSharesTreasury Contract Hash: " + contractHash);

    }
}
