package com.axlabs.neo.grantshares;

import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.http.HttpService;
import io.neow3j.types.Hash160;
import io.neow3j.wallet.Account;

import java.io.IOException;
import java.util.Properties;

public class Config {

    private static final String PROPS_FILE = "deploy.properties";
    private static String profile = null;
    private static Properties props;

    public static void setProfile(String profileName) {
        profile = profileName;
    }

    public static String getProperty(String name) {
        if (props == null) {
            props = new Properties();
            if (profile == null) {
                throw new RuntimeException("Profile must be set, e.g., 'test' for 'test.deploy.properties'");
            }
            try {
                props.load(Config.class.getClassLoader().getResourceAsStream(profile + "." + PROPS_FILE));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return props.getProperty(name);
    }

    public static long getIntProperty(String name) {
        return Long.valueOf(getProperty(name));
    }

    public static Neow3j getNeow3j() {
        return Neow3j.build(new HttpService(getProperty("node")));
    }

    public static Hash160 getGrantSharesGovHash() {
        return new Hash160(getProperty("grantsharesgov"));
    }

    public static Hash160 getGrantSharesTreasuryHash() {
        return new Hash160(getProperty("grantsharestreasury"));
    }

    public static Account getDeployAccount() {
        return Account.fromWIF(getProperty("deploy_account_wif"));
    }
}
