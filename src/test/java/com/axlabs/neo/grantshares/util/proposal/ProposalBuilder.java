package com.axlabs.neo.grantshares.util.proposal;

import com.axlabs.neo.grantshares.util.TestHelper;
import com.axlabs.neo.grantshares.util.proposal.intents.RequestForFunds;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.script.InteropService;
import io.neow3j.script.ScriptBuilder;
import io.neow3j.types.CallFlags;
import io.neow3j.types.Hash160;
import io.neow3j.utils.ArrayUtils;
import io.neow3j.wallet.Account;

import java.math.BigInteger;

import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.string;

public class ProposalBuilder {

    // region Request For Funds Proposal

    /**
     * Builds the script for creating a proposal that will release the provided Gas amount to the recipient on Neo X by
     * using the native bridge.
     * <p>
     * The intents include:
     * <ul>
     *     <li>Releasing the provided bridge fee amount in GAS tokens from the treasury to the bridge adapter.</li>
     *     <li>Releasing the requested GAS tokens from the treasury to the bridge adapter.</li>
     *     <li>Invoking the {@code bridge} function on the bridge adapter contract that will invoke the native bridge
     *     to deposit the corresponding amount to the provided address on Neo X.</li>
     * </ul>
     *
     * @param proposer  the proposer account.
     * @param data      the proposal data.
     * @param gov       the GrantSharesGov contract hash.
     * @param treasury  the GrantSharesTreasury contract hash.
     * @param adapter   the GrantSharesAdapter contract hash.
     * @param recipient the recipient address on Neo X.
     * @param amount    the amount of GAS tokens to be bridged.
     * @param bridgeFee the fee to be paid to the bridge.
     * @return the script to create a proposal for a fund request of Gas to an address on Neo X.
     */
    public static byte[] buildRequestForFundsTxScriptGas(Account proposer, ProposalData data, Hash160 gov,
            Hash160 treasury, Hash160 adapter, Hash160 recipient, BigInteger amount, BigInteger bridgeFee) {
        return buildRequestForFundsTxScript(GasToken.SCRIPT_HASH, proposer, data, gov, treasury, adapter, recipient,
                amount, bridgeFee
        );
    }

    /**
     * Builds the script for creating a proposal that will release the provided NEO amount to the recipient on Neo X by
     * using the native bridge.
     * <p>
     * The intents include:
     * <ul>
     *     <li>Releasing the provided bridge fee amount in GAS tokens from the treasury to the bridge adapter.</li>
     *     <li>Releasing the requested NEO tokens from the treasury to the bridge adapter.</li>
     *     <li>Invoking the {@code bridge} function on the bridge adapter contract that will invoke the native bridge
     *     to deposit the corresponding amount to the provided address on Neo X.</li>
     * </ul>
     *
     * @param proposer  the proposer account.
     * @param data      the proposal data.
     * @param gov       the GrantSharesGov contract hash.
     * @param treasury  the GrantSharesTreasury contract hash.
     * @param adapter   the GrantSharesAdapter contract hash.
     * @param recipient the recipient address on Neo X.
     * @param amount    the amount of NEO tokens to be bridged.
     * @param bridgeFee the fee to be paid to the bridge.
     * @return the script to create a proposal for a fund request of Neo to an address on Neo X.
     */
    public static byte[] buildRequestForFundsTxScriptNeo(Account proposer, ProposalData data, Hash160 gov,
            Hash160 treasury, Hash160 adapter, Hash160 recipient, BigInteger amount, BigInteger bridgeFee) {
        return buildRequestForFundsTxScript(NeoToken.SCRIPT_HASH, proposer, data, gov, treasury, adapter, recipient,
                amount, bridgeFee
        );
    }

    private static byte[] buildRequestForFundsTxScript(Hash160 token, Account proposer, ProposalData data,
            Hash160 gov, Hash160 treasury, Hash160 adapter, Hash160 recipient, BigInteger amount,
            BigInteger bridgeFee) {
        byte[] intentBytes = RequestForFunds.buildIntentsBytes(token, treasury, adapter, recipient, amount, bridgeFee);
        return buildCreateProposalScript(data, gov, intentBytes, proposer);
    }

    // endregion
    // region transaction script builder

    /**
     * Builds the script for creating a generic proposal on the GrantSharesGov contract.
     *
     * @param gov         the GrantSharesGov contract hash.
     * @param intentBytes the intents script.
     * @param proposer    the proposer of the proposal.
     * @return the proposal creation script.
     */
    public static byte[] buildCreateProposalScript(ProposalData proposalData, Hash160 gov, byte[] intentBytes,
            Account proposer) {

        ScriptBuilder b1 = new ScriptBuilder();
        int nrParams = 4; // proposer, intents, offchainUri, linkedProposal (reversed order of pushing params)
        b1.pushParam(integer(proposalData.getLinkedProposal()));
        b1.pushParam(string(proposalData.getOffChainUri()));
        byte[] startBytes = b1.toArray();

        ScriptBuilder b2 = new ScriptBuilder();
        b2.pushParam(hash160(proposer));
        b2.pushInteger(nrParams);
        b2.pack();

        b2.pushInteger(CallFlags.ALL.getValue());
        b2.pushData(TestHelper.GovernanceMethods.CREATE);
        b2.pushData(gov.toLittleEndianArray());
        b2.sysCall(InteropService.SYSTEM_CONTRACT_CALL);
        byte[] endBytes = b2.toArray();

        return ArrayUtils.concatenate(startBytes, intentBytes, endBytes);
    }

    // endregion

}
