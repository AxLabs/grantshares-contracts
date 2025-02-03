package com.axlabs.neo.grantshares.util.proposal.intents;

import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import io.neow3j.script.ScriptBuilder;
import io.neow3j.types.CallFlags;
import io.neow3j.types.Hash160;

import java.math.BigInteger;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;

/**
 * This class provides utility methods to build intents for fund request proposals in the GrantSharesAdapter contract.
 */
public class RequestForFunds {

    /**
     * This builds the required Intents for releasing the requested GAS tokens and bridging them to Neo X using
     * the native bridge (via the GrantSharesAdapter).
     *
     * @param treasury      the GrantSharesTreasury contract.
     * @param bridgeAdapter the GrantSharesAdapter contract.
     * @param bridgeFee     the fee to be paid to the bridge.
     * @param recipient     the recipient of the GAS tokens on Neo X.
     * @param amount        the amount of GAS tokens to be bridged.
     * @return the byte array representation of the intents.
     */
    public static byte[] intentBytes(Hash160 token, GrantSharesTreasuryContract treasury, Hash160 bridgeAdapter,
            Hash160 recipient, BigInteger amount, BigInteger bridgeFee) {

        ScriptBuilder b = new ScriptBuilder();
        // Replicate what happens in ScriptBuilder when pushing array(intents) (reversed order of pushing params)
        pushAdapterBridgeIntent(b, token, bridgeAdapter, recipient, amount);
        pushReleaseTokensIntent(b, token, treasury, bridgeAdapter, amount);
        pushReleaseTokensIntent(b, token, treasury, bridgeAdapter, bridgeFee);
        b.pushInteger(3); // Number of intents
        b.pack();
        return b.toArray();
    }

    private static void pushReleaseTokensIntent(ScriptBuilder b, Hash160 token, GrantSharesTreasuryContract from,
            Hash160 to, BigInteger amount) {

        // Replicate array(target, method, args, flags), i.e., ScriptBuilder.pushArray()
        b.pushParam(integer(CallFlags.ALL.getValue()));

        b.pushParam(integer(amount));
        b.pushParam(hash160(to));
        b.pushParam(hash160(token));
        b.pushInteger(3);
        b.pack();

        b.pushParam(string("releaseTokens"));
        b.pushParam(hash160(from.getScriptHash()));

        b.pushInteger(4);
        b.pack();
    }

    private static void pushAdapterBridgeIntent(ScriptBuilder b, Hash160 token, Hash160 bridgeAdaptor,
            Hash160 recipient, BigInteger amount) {
        // Replicate array(bridgeAdaptor, "bridge", [token, to, amount], CallFlags.ALL)
        b.pushInteger(CallFlags.ALL.getValue());

        b.pushInteger(amount);
        b.pushParam(hash160(recipient));
        b.pushParam(hash160(token));
        b.pushInteger(3);
        b.pack();

        b.pushParam(string("bridge"));
        b.pushParam(hash160(bridgeAdaptor));

        b.pushInteger(4);
        b.pack();
    }

}
