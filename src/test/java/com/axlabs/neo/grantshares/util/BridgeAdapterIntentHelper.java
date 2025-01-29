package com.axlabs.neo.grantshares.util;

import io.neow3j.contract.GasToken;
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

public class BridgeAdapterIntentHelper {

    public static class FundRequestData {
        private Hash160 token;
        private Hash160 recipient;
        private BigInteger amount;
        private String offchainUri;
        private int linkedProposal;

        public FundRequestData(Hash160 token, Hash160 recipient, BigInteger amount, String offchainUri,
                int linkedProposal) {
            this.token = token;
            this.recipient = recipient;
            this.amount = amount;
            this.offchainUri = offchainUri;
            this.linkedProposal = linkedProposal;
        }

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
        public byte[] buildIntentParamBytesForGasRequest(GrantSharesTreasuryContract treasury, Hash160 bridgeAdapter,
                BigInteger bridgeFee, Hash160 recipient, BigInteger amount) {

            ScriptBuilder b = new ScriptBuilder();
            // Replicate what happens in ScriptBuilder when pushing array(intents) (reversed order of pushing params)
            pushAdapterBridgeIntent(b, bridgeAdapter, recipient, amount);
            pushReleaseGasTokensIntent(b, treasury, bridgeAdapter, amount, bridgeFee);
            b.pushInteger(2); // Number of intents
            b.pack();
            return b.toArray();
        }

        /**
         * Builds the script for creating a generic proposal on the GrantSharesGov contract.
         *
         * @param gov         the GrantSharesGov contract.
         * @param intentBytes the byte array representation of the intents.
         * @param proposer    the proposer of the proposal.
         * @return the byte array representation of the script.
         */
        public byte[] buildCreateProposalScript(GrantSharesGovContract gov, byte[] intentBytes, Account proposer) {
            ScriptBuilder b1 = new ScriptBuilder();
            int nrParams = 4; // proposer, intents, offchainUri, linkedProposal (reversed order of pushing params)
            b1.pushParam(integer(linkedProposal));
            b1.pushParam(string(offchainUri));
            byte[] startBytes = b1.toArray();

            ScriptBuilder b2 = new ScriptBuilder();
            b2.pushParam(hash160(proposer));
            b2.pushInteger(nrParams);
            b2.pack();

            b2.pushInteger(CallFlags.ALL.getValue());
            b2.pushData(TestHelper.GovernanceMethods.CREATE);
            b2.pushData(gov.getScriptHash().toLittleEndianArray());
            b2.sysCall(InteropService.SYSTEM_CONTRACT_CALL);
            byte[] endBytes = b2.toArray();

            return ArrayUtils.concatenate(startBytes, intentBytes, endBytes);
        }

/*
        public byte[] buildFundRequestToNeoXForGas(GrantSharesGovContract gov,
                GrantSharesTreasuryContract treasury, Hash160 bridgeAdapter, BigInteger bridgeFee, Account proposer) {

            if (!token.equals(GasToken.SCRIPT_HASH)) {
                throw new IllegalStateException("This method is only for GasToken.");
            }

            ScriptBuilder b = new ScriptBuilder();
            int nrParams = 4; // proposer, intents, offchainUri, linkedProposal - reversed
            b.pushParam(integer(linkedProposal));
            b.pushParam(string(offchainUri));

            pushNeoXGasFundRequest(b, treasury, bridgeAdapter, bridgeFee, recipient, amount);

            b.pushParam(hash160(proposer));
            b.pushInteger(nrParams);
            b.pack();

            b.pushInteger(CallFlags.ALL.getValue());
            b.pushData(GovernanceMethods.CREATE);
            b.pushData(gov.getScriptHash().toLittleEndianArray());
            b.sysCall(InteropService.SYSTEM_CONTRACT_CALL);
            return b.toArray();

            // // Data from Neo X (event or storage)
            //        Hash160 token = GasToken.SCRIPT_HASH;
            //        Hash160 recipient = alice.getScriptHash();
            //        BigInteger amount = new GasToken(neow3j).toFractions(BigDecimal.TEN);
            //
            //        // Intent to release tokens to the adaptor
            //        Hash160 releaseTarget = treasury.getScriptHash();
            //        String releaseFunction = "releaseTokens";
            //        ContractParameter releaseArgs = array(
            //                token,
            //                bridgeAdapter.getScriptHash(),
            //                amount.add(DEFAULT_BRIDGE_FEE)
            //        );
            //        CallFlags releaseFlags = CallFlags.ALL;
            //        ContractParameter intent1 = createIntent(releaseTarget, releaseFunction, releaseArgs,
            //        releaseFlags);
            //
            //        // Intent to call bridge on the adaptor
            //        Hash160 bridgeTarget = bridgeAdapter.getScriptHash();
            //        String bridgeFunction = "bridge";
            //        ContractParameter bridgeArgs = array(token, recipient, amount);
            //        CallFlags bridgeFlags = CallFlags.ALL;
            //        ContractParameter intent2 = createIntent(bridgeTarget, bridgeFunction, bridgeArgs, bridgeFlags);
        }

        private void pushNeoXGasFundRequest(ScriptBuilder b, GrantSharesTreasuryContract treasury,
                Hash160 bridgeAdaptor, BigInteger bridgeFee, Hash160 recipient, BigInteger amount) {

            // Replicate what happens in ScriptBuilder when pushing array(intents). Reverse order of pushing intents.
            pushAdapterBridgeIntent(b, bridgeAdaptor, recipient, amount);
            pushReleaseGasTokensIntent(b, treasury, bridgeAdaptor, amount, bridgeFee);
            b.pushInteger(2); // Number of intents
            b.pack();
        }
 */

        private void pushReleaseGasTokensIntent(ScriptBuilder b, GrantSharesTreasuryContract treasury,
                Hash160 bridgeAdaptor, BigInteger amount, BigInteger bridgeFee) {

            // Replicate array(target, method, args, flags), i.e., ScriptBuilder.pushArray()
            b.pushParam(integer(CallFlags.ALL.getValue()));

            pushReleaseTokenArgsForGAS(b, bridgeAdaptor, amount.add(bridgeFee));

            b.pushParam(string("releaseTokens"));
            b.pushParam(hash160(treasury.getScriptHash()));

            b.pushInteger(4);
            b.pack();
        }

        private void pushReleaseTokenArgsForGAS(ScriptBuilder b, Hash160 bridgeAdaptor, BigInteger amountWithFee) {
            b.pushParam(integer(amountWithFee));
            b.pushParam(hash160(bridgeAdaptor));
            b.pushParam(hash160(GasToken.SCRIPT_HASH));
            b.pushInteger(3);
            b.pack();
        }

        private void pushAdapterBridgeIntent(ScriptBuilder b, Hash160 bridgeAdaptor, Hash160 recipient,
                BigInteger amount) {

            // Replicate array(bridgeAdaptor, "bridge", [token, to, amount], CallFlags.ALL)
            b.pushInteger(CallFlags.ALL.getValue());

            b.pushInteger(amount);
            b.pushParam(hash160(recipient));
            b.pushParam(hash160(GasToken.SCRIPT_HASH));
            b.pushInteger(3);
            b.pack();

            b.pushParam(string("bridge"));
            b.pushParam(hash160(bridgeAdaptor));

            b.pushInteger(4);
            b.pack();
        }
    }

}
