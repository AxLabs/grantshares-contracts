package com.axlabs.neo.grantshares.util.proposal;

import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static io.neow3j.utils.Numeric.hexStringToByteArray;
import static io.neow3j.utils.Numeric.reverseHexString;
import static io.neow3j.utils.Numeric.toHexString;
import static io.neow3j.utils.Numeric.toHexStringNoPrefix;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ProposalBuilderTest {

    public static final Hash160 GS_GOV = new Hash160("0x2980dba54983777175780c59f64c59d2fbd5df48");
    public static final Hash160 GS_TREASURY = new Hash160("0x95434943e07ab980dd9fbe4edc0edefb17a13517");
    public static final Hash160 BRIDGE_ADAPTER = new Hash160("0x8e5266f736a4feec36a75378101bae1286e45bd2");

    @Test
    public void testBuildCreateProposalScript() {
        Account proposer = Account.fromWIF("KzMdcC173uX5jYBEED3MuKVAAHWKq8DjuevMv416QcyH5Y5X1vYL");
        ProposalData proposalData = new ProposalData("offchainUri", 12);

        byte[] someIntentBytes = hexStringToByteArray("0x34125e87acd5984679");

        // The intent bytes are random. This test just verifies that these bytes are included correctly and the
        // prefix and suffix are correctly set.
        byte[] createProposalScript = ProposalBuilder.buildCreateProposalScript(proposalData, GS_GOV, someIntentBytes,
                proposer
        );

        //0x1c0c0b6f6666636861696e55726934125e87acd59846790c14989b16363233ccc699c6a9a1840ab8cd1934d58214c01f0c0e63726561746550726f706f73616c0c1448dfd5fbd2594cf6590c787571778349a5db802941627d5b52
        String expectedScript = "0x1c" + // linked proposal
                "0c" + "0b" + "6f6666636861696e557269" + // pushdata + size + offchain uri
                toHexStringNoPrefix(someIntentBytes) +
                "0c" + "14" + reverseHexString(toHexStringNoPrefix(proposer.getScriptHash().toArray())) +
                "14" + "c0" + // 4 args + pack

                "1f" + // call flags all
                // pushdata + size + createProposal function
                "0c" + "0e" + toHexStringNoPrefix("createProposal".getBytes()) +
                // pushdata + size + governance contract
                "0c" + "14" + reverseHexString(toHexStringNoPrefix(GS_GOV.toArray())) +
                // syscall contract call
                "41" + "627d5b52";
        assertThat(toHexString(createProposalScript), is(expectedScript));
    }

    @Test
    public void testRequestForFundsGas() {
        Account proposer = Account.fromWIF("KzMdcC173uX5jYBEED3MuKVAAHWKq8DjuevMv416QcyH5Y5X1vYL");
        Hash160 recipient = new Hash160("0x69ecca587293047be4c59159bf8bc399985c160d");
        BigInteger amount = new BigInteger("1000000000");
        BigInteger bridgeFee = new BigInteger("10000000");
        ProposalData proposalData = new ProposalData("execute_proposal_with_bridge_adapter_gas", -1);

        byte[] txScript = ProposalBuilder.requestForFundsGas(proposer, proposalData, GS_GOV, GS_TREASURY,
                BRIDGE_ADAPTER, recipient, amount, bridgeFee
        );
        assertThat(toHexString(txScript),
                is("0x0f0c28657865637574655f70726f706f73616c5f776974685f6272696467655f616461707465725f6761731f0200ca9a3b0c140d165c9899c38bbf5991c5e47b04937258caec690c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c066272696467650c14d25be48612ae1b107853a736ecfea436f766528e14c01f0200ca9a3b0c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c01f02809698000c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c013c00c14989b16363233ccc699c6a9a1840ab8cd1934d58214c01f0c0e63726561746550726f706f73616c0c1448dfd5fbd2594cf6590c787571778349a5db802941627d5b52")
        );
    }

    @Test
    public void testRequestForFundsNeo() {
        Account proposer = Account.fromWIF("KzPJdAv8xXTMUTW94bzk77wUUZh49jWCgGtDhTVgn928mjcGR4kK");
        Hash160 recipient = new Hash160("0x1cfb4032e4c5f394b8e5ec78f2cf148230af5090");
        BigInteger amount = new BigInteger("10");
        BigInteger bridgeFee = new BigInteger("10000000");
        ProposalData proposalData = new ProposalData("execute_proposal_with_bridge_adapter_neo", 0);

        byte[] txScript = ProposalBuilder.requestForFundsNeo(proposer, proposalData, GS_GOV, GS_TREASURY,
                BRIDGE_ADAPTER, recipient, amount, bridgeFee
        );
        assertThat(toHexString(txScript),
                is("0x100c28657865637574655f70726f706f73616c5f776974685f6272696467655f616461707465725f6e656f1f1a0c149050af308214cff278ece5b894f3c5e43240fb1c0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c066272696467650c14d25be48612ae1b107853a736ecfea436f766528e14c01f1a0c14d25be48612ae1b107853a736ecfea436f766528e0c14f563ea40bc283d4d0e05c48ea305b3f2a07340ef13c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c01f02809698000c14d25be48612ae1b107853a736ecfea436f766528e0c14cf76e28bd0062c4a478ee35561011319f3cfa4d213c00c0d72656c65617365546f6b656e730c141735a117fbde0edc4ebe9fdd80b97ae04349439514c013c00c14c20704128f809d6f47f8596eafe0ea779538ddfa14c01f0c0e63726561746550726f706f73616c0c1448dfd5fbd2594cf6590c787571778349a5db802941627d5b52")
        );
    }

}
