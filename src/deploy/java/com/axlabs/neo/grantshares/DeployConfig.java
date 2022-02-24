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

public class DeployConfigs {

    //////////////////////////////////
    // GrantSharesGov Deploy Config //
    //////////////////////////////////

    // param names
    static final String REVIEW_LENGTH_KEY = "review_len";
    static final String VOTING_LENGTH_KEY = "voting_len";
    static final String TIMELOCK_LENGTH_KEY = "timelock_len";
    static final String MIN_ACCEPTANCE_RATE_KEY = "min_accept_rate";
    static final String MIN_QUORUM_KEY = "min_quorum";
    static final String THRESHOLD_KEY = "threshold";
    // param values
    static final int REVIEW_LENGTH = 0; // no review phase
    static final int VOTING_LENGTH = 600000; // 10 min
    static final int TIMELOCK_LENGTH = 300000; // 5 min
    static final int MIN_ACCEPTANCE_RATE = 50;
    static final int MIN_QUORUM = 50;
    static final int MEMBERS_MULTI_SIG_THRESHOLD = 50;

    /**
     * Gets the deploy configuration for the governance contract. Requires that there is a
     * 'resources/deploy.properties' file that contains the initial members in the following format:
     * <pre>
     *  member1=03a140e068b2e1fffdd901acf96cdc35676687b25fba5f4ed267ad4ec463bf4dc3
     *  member2=0351408068b0e1f7fdd901acf96cdc35676687b25fba5f4ed267ad4ec483bc7ac5
     *  ...
     * </pre>
     */
    static ContractParameter getGovDeployConfig() throws IOException {
        Properties prop = new Properties();
        prop.load(DeployConfigs.class.getClassLoader().getResourceAsStream("deploy.properties"));
        List<ContractParameter> members = new ArrayList<>();
        int i = 1;
        String val = prop.getProperty("member" + i++);
        while (val != null) {
            members.add(publicKey(val));
            val = prop.getProperty("member" + i++);
        }
        return array(
                members,
                array(
                        REVIEW_LENGTH_KEY, REVIEW_LENGTH,
                        VOTING_LENGTH_KEY, VOTING_LENGTH,
                        TIMELOCK_LENGTH_KEY, TIMELOCK_LENGTH,
                        MIN_ACCEPTANCE_RATE_KEY, MIN_ACCEPTANCE_RATE,
                        MIN_QUORUM_KEY, MIN_QUORUM,
                        THRESHOLD_KEY, MEMBERS_MULTI_SIG_THRESHOLD
                ));
    }

    ///////////////////////////////////////
    // GrantSharesTreasury Deploy Config //
    ///////////////////////////////////////

    // whitelisted tokens
    static final Hash160 NEO = NeoToken.SCRIPT_HASH;
    static final int NEO_MAX_AMOUNT = 1000;
    static final Hash160 GAS = GasToken.SCRIPT_HASH;
    static final int GAS_MAX_AMOUNT = 10000;

    static final int FUNDERS_MULTI_SIG_THRESHOLD = 75;

    /**
     * Gets the deploy configuration for the treasurty contract. Requires that there is a
     * 'resources/deploy.properties' file that contains the initial funders in the following format:
     * <pre>
     *  funder1=03a140e068b2e1fffdd901acf96cdc35676687b25fba5f4ed267ad4ec463bf4dc3
     *  funder2=0351408068b0e1f7fdd901acf96cdc35676687b25fba5f4ed267ad4ec483bc7ac5
     *  ...
     * </pre>
     */
    static ContractParameter getTreasuryDeployConfig(Hash160 grantSharesGovHash) throws IOException {
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(NEO, NEO_MAX_AMOUNT);
        tokens.put(GAS, GAS_MAX_AMOUNT);

        Properties prop = new Properties();
        prop.load(DeployConfigs.class.getClassLoader().getResourceAsStream("deploy.properties"));
        List<ContractParameter> funders = new ArrayList<>();
        int i = 1;
        String val = prop.getProperty("funder" + i++);
        while (val != null) {
            funders.add(publicKey(val));
            val = prop.getProperty("funder" + i++);
        }
        return array(
                hash160(grantSharesGovHash),
                funders,
                map(tokens),
                FUNDERS_MULTI_SIG_THRESHOLD);
    }

}
