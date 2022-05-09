package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.GrantSharesTreasuryContract;
import com.axlabs.neo.grantshares.util.IntentParam;
import com.axlabs.neo.grantshares.util.TestHelper;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.contract.GasToken;
import io.neow3j.contract.NeoToken;
import io.neow3j.contract.SmartContract;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.test.DeployContext;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash160;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.MULTI_SIG_THRESHOLD_RATIO;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = {GrantSharesGov.class, GrantSharesTreasuryOld.class}, blockTime = 1,
        configFile = "default.neo-express", batchFile = "setup.batch")
public class TreasuryMigrationTest {
    static final int NEO_MAX_AMOUNT = 1000;
    static final int GAS_MAX_AMOUNT = 100000000;

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static GrantSharesTreasuryContract treasury;
    private static Account alice; // Set to be a DAO member and funder
    private static Account bob; // Set to be a DAO member and funder
    private static Account charlie;

    @DeployConfig(GrantSharesGov.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(ext.getAccount(ALICE), ext.getAccount(BOB)));
        return config;
    }

    @DeployConfig(GrantSharesTreasuryOld.class)
    public static DeployConfiguration deployConfigTreasury(DeployContext ctx) throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        // owner
        SmartContract gov = ctx.getDeployedContract(GrantSharesGov.class);

        // funders
        Account alice = ext.getAccount(ALICE);
        Account bob = ext.getAccount(BOB);
        ContractParameter funders = array(
                array(alice.getScriptHash(), array(alice.getECKeyPair().getPublicKey())),
                array(bob.getScriptHash(), array(bob.getECKeyPair().getPublicKey()))
        );

        // whitelisted tokens
        Map<Hash160, Integer> tokens = new HashMap<>();
        tokens.put(NeoToken.SCRIPT_HASH, NEO_MAX_AMOUNT);
        tokens.put(GasToken.SCRIPT_HASH, GAS_MAX_AMOUNT);
        ContractParameter tokensParam = map(tokens);

        config.setDeployParam(array(gov.getScriptHash(), funders, tokensParam, MULTI_SIG_THRESHOLD_RATIO));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        neow3j.allowTransmissionOnFault();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGov.class).getScriptHash(), neow3j);
        treasury = new GrantSharesTreasuryContract(
                ext.getDeployedContract(GrantSharesTreasuryOld.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);
    }

    @Test
    public void updateContractAndMigrateStorage() throws Throwable {
        assertThat(treasury.getWhitelistedTokens().get(GasToken.SCRIPT_HASH).intValue(), is(GAS_MAX_AMOUNT));

        CompilationUnit res = new Compiler().compile(GrantSharesTreasury.class.getCanonicalName());
        Map<Hash160, Long> newWhitelistedTokens = new HashMap<>();
        final int newGasTokenLimit = 2000000000;
        newWhitelistedTokens.put(GasToken.SCRIPT_HASH, (long) newGasTokenLimit);
        ContractParameter i = IntentParam.updateContractProposal(treasury.getScriptHash(),
                res.getNefFile(),
                res.getManifest(),
                map(newWhitelistedTokens));

        int id = TestHelper.createAndEndorseProposal(gov, neow3j, alice, bob, array(i),
                "updateContractAndMigrateStorage");
        ext.fastForwardOneBlock(PHASE_LENGTH);
        TestHelper.voteForProposal(gov, neow3j, id, alice);
        ext.fastForwardOneBlock(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id).signers(AccountSigner.none(bob)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        assertThat(treasury.getWhitelistedTokens().get(GasToken.SCRIPT_HASH).intValue(), is(newGasTokenLimit));
        assertThat(treasury.getWhitelistedTokens().get(NeoToken.SCRIPT_HASH).intValue(), is(NEO_MAX_AMOUNT));

        List<NeoApplicationLog.Execution.Notification> notifications = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications();
        List<NeoApplicationLog.Execution.Notification> migratedEvents = notifications.stream()
                .filter(n -> n.getEventName().equals("WhitelistedTokenMigrated")).collect(Collectors.toList());
        assertThat(migratedEvents.size(), is(1));
        assertThat(migratedEvents.get(0).getState().getList().get(0).getAddress(), is(GasToken.SCRIPT_HASH.toAddress()));
        assertThat(migratedEvents.get(0).getState().getList().get(1).getInteger().intValue(), is(newGasTokenLimit));
    }
}
