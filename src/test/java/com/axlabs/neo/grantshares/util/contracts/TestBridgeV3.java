package com.axlabs.neo.grantshares.util.contracts;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.Struct;
import io.neow3j.devpack.contracts.FungibleToken;
import io.neow3j.devpack.contracts.GasToken;
import io.neow3j.devpack.contracts.NeoToken;
import io.neow3j.devpack.contracts.StdLib;
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
public class TestBridgeV3 {

    private static final int FEE_KEY = 0x00;

    // @EventParameterNames({"From", "Recipient", "Amount"}) // Requires neow3j v3.23.0
    @DisplayName("NativeDeposit")
    public static Event3Args<Hash160, Hash160, Integer> nativeDeposit;

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

    // This dummy struct is used to imitate the bridge's call to the native StdLib contract when deserializing the
    // stored bridge data upon fetching the fee value.
    @Struct
    private static class StructWithValueOne {
        public int one;

        StructWithValueOne() {
            one = 1;
        }
    }

    // Returns 1, but includes a call to StdLib for testing purposes to imitate the call to StdLib that the bridge does.
    private static int getOneWithStdLibCall() {
        return ((StructWithValueOne) new StdLib().deserialize(
                new ByteString(new byte[]{0x40, 0x01, 0x21, 0x01, 0x01}))).one;
    }

    @Safe
    public static int nativeDepositFee() {
        return Storage.getInt(getReadOnlyContext(), FEE_KEY) * getOneWithStdLibCall();
    }

    public static void depositNative(Hash160 from, Hash160 to, int amount, int maxFee) {
        if (nativeDepositFee() > maxFee) abort("insufficient max fee");

        Hash160 executingScriptHash = getExecutingScriptHash();

        if (!new GasToken().transfer(from, executingScriptHash, nativeDepositFee(), null)) {
            abort("fee transfer failed");
        }

        if (!new GasToken().transfer(from, executingScriptHash, amount, null)) {
            abort("native transfer failed");
        }
        nativeDeposit.fire(from, to, amount);
    }

    @Safe
    public static int tokenDepositFee(Hash160 token) {
        return Storage.getInt(getReadOnlyContext(), FEE_KEY) * getOneWithStdLibCall();
    }

    public static void depositToken(Hash160 token, Hash160 from, Hash160 to, int amount, int maxFee) {
        if (nativeDepositFee() > maxFee) abort("insufficient max fee");

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
