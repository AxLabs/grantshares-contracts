package com.axlabs.neo.grantshares.util.contracts;

import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

import static io.neow3j.devpack.Helper.abort;
import static io.neow3j.devpack.Runtime.getCallingScriptHash;
import static io.neow3j.devpack.Runtime.getExecutingScriptHash;
import static io.neow3j.devpack.Storage.getReadOnlyContext;
import static io.neow3j.devpack.Storage.getStorageContext;

/**
 * This is a test contract to mimic the behavior of the bridge contract.
 */
@Permission(contract = "*", methods = "*")
public class TestBridge {

    private static final int FEE_KEY = 0x00;

    // @EventParameterNames({"From", "Recipient", "Amount"}) // Requires neow3j v3.23.0
    @DisplayName("GasDeposit")
    public static Event3Args<Hash160, Hash160, Integer> gasDeposit;

    // @EventParameterNames({"From", "Recipient", "Amount"}) // Requires neow3j v3.23.0
    @DisplayName("TokenDeposit")
    public static Event4Args<Hash160, Hash160, Hash160, Integer> tokenDeposit;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Storage.put(getStorageContext(), FEE_KEY, (int) data);
        }
    }

    @OnNEP17Payment
    public static void onNEP17Payment(Hash160 from, int amount, Object data) {
        Hash160 callingScriptHash = getCallingScriptHash();
        if (callingScriptHash.equals(new GasToken().getHash())) {
            if (from == null) {
                // Accept Gas rewards from holding neo.
                return;
            } else if (data == null) {
                return;
            } else {
                abort("No data accepted");
            }
            return;
        } else if (callingScriptHash.equals(new NeoToken().getHash())) {
            if (data != null) {
                abort("No data accepted");
            }
            return;
        } else {
            abort("Unsupported token");
        }
    }

    @Safe
    public static int gasDepositFee() {
        return Storage.getInt(getReadOnlyContext(), FEE_KEY);
    }

    public static void depositGas(Hash160 from, Hash160 to, int amountIncludingFee, int maxFee) {
        if (gasDepositFee() > maxFee) abort("insufficient max fee");

        Hash160 executingScriptHash = getExecutingScriptHash();

        if (!new GasToken().transfer(from, executingScriptHash, amountIncludingFee, null)) {
            abort("gas transfer failed");
        }
        gasDeposit.fire(from, to, amountIncludingFee - gasDepositFee());
    }

    @Safe
    public static int tokenDepositFee(Hash160 token) {
        return Storage.getInt(getReadOnlyContext(), FEE_KEY);
    }

    public static void depositToken(Hash160 token, Hash160 from, Hash160 to, int amount, int maxFee) {
        if (gasDepositFee() > maxFee) abort("insufficient max fee");

        Hash160 executingScriptHash = getExecutingScriptHash();
        if (!new GasToken().transfer(from, executingScriptHash, tokenDepositFee(token), null)) {
            abort("fee transfer failed (token)");
        }
        if (!new FungibleToken(token).transfer(from, executingScriptHash, amount, null)) {
            abort("native transfer failed");
        }
        tokenDeposit.fire(token, from, to, amount);
    }

}
