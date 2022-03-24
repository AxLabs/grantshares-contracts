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

import static com.axlabs.neo.grantshares.Config.getNeow3j;

public class Deployment {

    public static final String PROFILE = "dev";

    public static void main(String[] args) throws Throwable {
        Config.setProfile(PROFILE);
        AccountSigner signer = AccountSigner.none(Config.getDeployAccount());
        Hash160 grantSharesGovHash = deployGrantSharesGov(signer);
        deployGrantSharesTreasury(grantSharesGovHash, signer);
    }

    private static Hash160 deployGrantSharesGov(AccountSigner signer) throws Throwable {
        CompilationUnit res = new Compiler().compile(GrantSharesGov.class.getCanonicalName());
        TransactionBuilder builder = new ContractManagement(getNeow3j())
                .deploy(res.getNefFile(), res.getManifest(), DeployConfig.getGovDeployConfig())
                .signers(signer)
                .throwIfSenderCannotCoverFees(() -> new RuntimeException("Cannot cover fees"));

        Hash256 txHash = builder.sign().send().getSendRawTransaction().getHash();
        System.out.println("GrantSharesGov Deploy Transaction Hash: " + txHash.toString());
        Await.waitUntilTransactionIsExecuted(txHash, getNeow3j());

        NeoApplicationLog log = getNeow3j().getApplicationLog(txHash).send().getApplicationLog();
        if (log.getExecutions().get(0).getState().equals(NeoVMStateType.FAULT)) {
            throw new Exception("Failed to deploy smart contract. NeoVM " +
                    "error message: " + log.getExecutions().get(0).getException());
        }
        Hash160 contractHash = SmartContract.calcContractHash(signer.getScriptHash(),
                res.getNefFile().getCheckSumAsInteger(), res.getManifest().getName());
        System.out.println("GrantSharesGov Contract Hash: " + contractHash);
        return contractHash;
    }

    private static void deployGrantSharesTreasury(Hash160 grantSharesGovHash, AccountSigner signer) throws Throwable {
        CompilationUnit res = new Compiler().compile(GrantSharesTreasury.class.getCanonicalName());
        TransactionBuilder builder = new ContractManagement(getNeow3j())
                .deploy(res.getNefFile(), res.getManifest(),
                        DeployConfig.getTreasuryDeployConfig(grantSharesGovHash))
                .signers(signer)
                .throwIfSenderCannotCoverFees(() -> new RuntimeException("Cannot cover fees"));

        Hash256 txHash = builder.sign().send().getSendRawTransaction().getHash();
        System.out.println("GrantSharesTreasury Deploy Transaction Hash: " + txHash.toString());
        Await.waitUntilTransactionIsExecuted(txHash, getNeow3j());

        NeoApplicationLog log = getNeow3j().getApplicationLog(txHash).send().getApplicationLog();
        if (log.getExecutions().get(0).getState().equals(NeoVMStateType.FAULT)) {
            throw new Exception("Failed to deploy smart contract. NeoVM " +
                    "error message: " + log.getExecutions().get(0).getException());
        }
        Hash160 contractHash = SmartContract.calcContractHash(signer.getScriptHash(),
                res.getNefFile().getCheckSumAsInteger(), res.getManifest().getName());
        System.out.println("GrantSharesTreasury Contract Hash: " + contractHash);

    }
}
