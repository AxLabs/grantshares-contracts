package com.axlabs.neo.grantshares.bridgeadapter;

import io.neow3j.wallet.Account;
import io.neow3j.wallet.Wallet;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AccountHelper {

    static Account getBridgeAdapterOwner() throws Exception {
        String walletPath = System.getenv("GS_BRIDGE_ADAPTER_OWNER_WALLET_PATH");
        String walletPassword = System.getenv("GS_BRIDGE_ADAPTER_OWNER_PASSWORD");

        Account deployerAndInitialOwner = getDefaultAccountFromWallet(walletPath);
        deployerAndInitialOwner.decryptPrivateKey(walletPassword);
        return deployerAndInitialOwner;
    }

    private static Account getDefaultAccountFromWallet(String walletPath) throws Exception {
        Path path = Paths.get(walletPath);
        if (!Files.exists(path)) {
            throw new Exception(String.format("Provided wallet file '%s' does not exist.", walletPath));
        }
        Wallet wallet = Wallet.fromNEP6Wallet(path.toFile());
        return wallet.getDefaultAccount();
    }

}
