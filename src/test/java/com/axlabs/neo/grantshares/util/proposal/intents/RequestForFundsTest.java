package com.axlabs.neo.grantshares.util.proposal.intents;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.types.Hash160;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static io.neow3j.utils.Numeric.toHexString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RequestForFundsTest {

    private static final Hash160 TREASURY = new Hash160("0x95434943e07ab980dd9fbe4edc0edefb17a13517");
    private static final Hash160 BRIDGE_ADAPTER = new Hash160("0x8e5266f736a4feec36a75378101bae1286e45bd2");

    @Test
    public void testIntentBytes_RequestForFunds_Neo() {
        Hash160 recipient = new Hash160("0x1cfb4032e4c5f394b8e5ec78f2cf148230af5090");
        BigInteger amount = new BigInteger("10");
        BigInteger bridgeFee = new BigInteger("10000000");

        byte[] intentBytes = RequestForFunds.buildIntentsBytes(NeoToken.SCRIPT_HASH, TREASURY, BRIDGE_ADAPTER,
                recipient, amount, bridgeFee
        );

        String expectedIntents =
                "0x1f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14d25be48612ae1b107853a736ecfea436f766528e14c01f1a0c14d25be48612ae1b107853a736ecfea436f766528e0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c01f02809698000c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c013c0";
        assertThat(toHexString(intentBytes), is(expectedIntents));
    }

    @Test
    public void testIntentBytes_RequestForFunds_Gas() {
        Hash160 recipient = new Hash160("0x69ecca587293047be4c59159bf8bc399985c160d");
        BigInteger amount = new BigInteger("1000000000");
        BigInteger bridgeFee = new BigInteger("10000000");

        byte[] intentBytes = RequestForFunds.buildIntentsBytes(GasToken.SCRIPT_HASH, TREASURY, BRIDGE_ADAPTER,
                recipient, amount, bridgeFee
        );

        String expectedIntents =
                "0x1f0200ca9a3b0c140d165c9899c38bbf5991c5e47b04937258caec690c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c066272696467650c14d25be48612ae1b107853a736ecfea436f766528e14c01f0200ca9a3b0c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c01f02809698000c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c013c0";
        assertThat(toHexString(intentBytes), is(expectedIntents));
    }

}
