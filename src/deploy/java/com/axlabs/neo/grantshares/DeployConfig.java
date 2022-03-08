package com.axlabs.neo.grantshares;

import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.map;
import static io.neow3j.types.ContractParameter.publicKey;

public class DeployConfig {

    // param names
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String THRESHOLD_KEY = "threshold";

    /**
     * Gets the deploy configuration for the governance contract. Requires that there is a
     * 'resources/deploy.properties' file that contains the initial members in the following format:
     * <pre>
     *  member1=03a140e068b2e1fffdd901acf96cdc35676687b25fba5f4ed267ad4ec463bf4dc3
     *  member2=0351408068b0e1f7fdd901acf96cdc35676687b25fba5f4ed267ad4ec483bc7ac5
     *  ...
     * </pre>
     * And all the initial parameter values of the contract's parameters.
     */
    static ContractParameter getGovDeployConfig() {
        List<ContractParameter> members = new ArrayList<>();
        int i = 1;
        String val = Config.getProperty("member" + i++);
        while (val != null) {
            members.add(publicKey(val));
            val = Config.getProperty("member" + i++);
        }
        return array(
                members,
                array(
                        REVIEW_LENGTH_KEY, Config.getIntProperty(REVIEW_LENGTH_KEY),
                        VOTING_LENGTH_KEY, Config.getIntProperty(VOTING_LENGTH_KEY),
                        TIMELOCK_LENGTH_KEY, Config.getIntProperty(TIMELOCK_LENGTH_KEY),
                        MIN_ACCEPTANCE_RATE_KEY, Config.getIntProperty(MIN_ACCEPTANCE_RATE_KEY),
                        MIN_QUORUM_KEY, Config.getIntProperty(MIN_QUORUM_KEY),
                        THRESHOLD_KEY, Config.getIntProperty(THRESHOLD_KEY)
                ));
    }

    /**
     * Gets the deploy configuration for the treasurty contract. Requires that there is a
     * 'resources/deploy.properties' file that contains the initial funders in the following format:
     * <pre>
     *  funder1=03a140e068b2e1fffdd901acf96cdc35676687b25fba5f4ed267ad4ec463bf4dc3
     *  funder2=0351408068b0e1f7fdd901acf96cdc35676687b25fba5f4ed267ad4ec483bc7ac5
     *  ...
     * </pre>
     * And the token maximum for NEO and GAS in the properties "neo_token_max" and "gas_token_max".
     */
    static ContractParameter getTreasuryDeployConfig(Hash160 grantSharesGovHash) {
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(new Hash160(Config.getProperty("neo_token")), Config.getIntProperty("neo_token_max"));
        tokens.put(new Hash160(Config.getProperty("gas_token")), Config.getIntProperty("gas_token_max"));

        List<ContractParameter> funders = new ArrayList<>();
        int i = 1;
        String val = Config.getProperty("funder" + i++);
        while (val != null) {
            funders.add(publicKey(val));
            val = Config.getProperty("funder" + i++);
        }
        return array(
                hash160(grantSharesGovHash),
                funders,
                map(tokens),
                Config.getIntProperty(THRESHOLD_KEY));
    }

}
