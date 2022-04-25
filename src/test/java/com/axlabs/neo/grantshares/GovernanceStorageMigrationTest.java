package com.axlabs.neo.grantshares;

import com.axlabs.neo.grantshares.util.GrantSharesGovContract;
import com.axlabs.neo.grantshares.util.ProposalStruct;
import com.axlabs.neo.grantshares.util.TestHelper;
import io.neow3j.compiler.CompilationUnit;
import io.neow3j.compiler.Compiler;
import io.neow3j.protocol.Neow3j;
import io.neow3j.protocol.ObjectMapperFactory;
import io.neow3j.protocol.core.response.NeoApplicationLog;
import io.neow3j.protocol.core.stackitem.StackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import io.neow3j.test.DeployConfig;
import io.neow3j.test.DeployConfiguration;
import io.neow3j.transaction.AccountSigner;
import io.neow3j.types.CallFlags;
import io.neow3j.types.ContractParameter;
import io.neow3j.types.Hash256;
import io.neow3j.utils.Await;
import io.neow3j.wallet.Account;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Collectors;

import static com.axlabs.neo.grantshares.util.TestHelper.ALICE;
import static com.axlabs.neo.grantshares.util.TestHelper.BOB;
import static com.axlabs.neo.grantshares.util.TestHelper.CHARLIE;
import static com.axlabs.neo.grantshares.util.TestHelper.PHASE_LENGTH;
import static com.axlabs.neo.grantshares.util.TestHelper.VOTING_LENGTH_KEY;
import static com.axlabs.neo.grantshares.util.TestHelper.prepareDeployParameter;
import static io.neow3j.types.ContractParameter.any;
import static io.neow3j.types.ContractParameter.array;
import static io.neow3j.types.ContractParameter.byteArray;
import static io.neow3j.types.ContractParameter.hash160;
import static io.neow3j.types.ContractParameter.integer;
import static io.neow3j.types.ContractParameter.publicKey;
import static io.neow3j.types.ContractParameter.string;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

@ContractTest(contracts = GrantSharesGovOld.class, blockTime = 1, configFile = "default.neo-express",
        batchFile = "setup.batch")
public class GovernanceStorageMigrationTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private static Neow3j neow3j;
    private static GrantSharesGovContract gov;
    private static Account alice; // Set to be a DAO member.
    private static Account bob; // Set to be a DAO member.
    private static Account charlie;

    @DeployConfig(GrantSharesGovOld.class)
    public static DeployConfiguration deployConfig() throws Exception {
        DeployConfiguration config = new DeployConfiguration();
        config.setDeployParam(prepareDeployParameter(ext.getAccount(ALICE), ext.getAccount(BOB)));
        return config;
    }

    @BeforeAll
    public static void setUp() throws Throwable {
        neow3j = ext.getNeow3j();
        gov = new GrantSharesGovContract(ext.getDeployedContract(GrantSharesGovOld.class).getScriptHash(), neow3j);
        alice = ext.getAccount(ALICE);
        bob = ext.getAccount(BOB);
        charlie = ext.getAccount(CHARLIE);

        ContractParameter intent1 = array(hash160(gov.getScriptHash()), string("addMember"),
                        array(publicKey(bob.getECKeyPair().getPublicKey())));
        ContractParameter intent2 = array(hash160(gov.getScriptHash()), string("changeParam"),
                array(VOTING_LENGTH_KEY, 100));
        Hash256 txHash = gov.createProposal(alice.getScriptHash(), "proposal1", -1, intent1, intent2)
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
        txHash = gov.createProposal(alice.getScriptHash(), "proposal2", -1, intent1)
                .signers(AccountSigner.calledByEntry(alice)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(txHash, neow3j);
    }

    @Test
    public void updateContractAndMigrateStorage() throws Throwable {
        List<StackItem> proposal = gov.callInvokeFunction("getProposal", asList(integer(0)))
                .getInvocationResult().getStack().get(0).getList();
        List<StackItem> intent = proposal.get(11).getList();
        assertThat(intent.size(), is(2)); // two intents in the first proposal
        // the intents have 3 attributes (no CallFlags yet)
        assertThat(proposal.get(11).getList().get(0).getList().size(), is(3));

        CompilationUnit res = new Compiler().compile(GrantSharesGov.class.getCanonicalName());

        String manifestString = ObjectMapperFactory.getObjectMapper().writeValueAsString(res.getManifest());
        ContractParameter i = array(hash160(gov.getScriptHash()), string("updateContract"),
            array(byteArray(res.getNefFile().toArray()), string(manifestString), any(null)));
        int id = TestHelper.createAndEndorseProposal(gov, neow3j, charlie, alice, array(i), "updateContract");
        ext.fastForward(PHASE_LENGTH);
        TestHelper.voteForProposal(gov, neow3j, id, alice);
        ext.fastForward(PHASE_LENGTH + PHASE_LENGTH);
        Hash256 tx = gov.execute(id).signers(AccountSigner.none(bob)).sign().send().getSendRawTransaction().getHash();
        Await.waitUntilTransactionIsExecuted(tx, neow3j);

        ProposalStruct p = gov.getProposal(0);
        assertThat(p.intents.size(), is(2));
        assertThat(p.intents.get(0).callFlags, is((int) CallFlags.ALL.getValue()));

        p = gov.getProposal(1);
        assertThat(p.intents.size(), is(1));
        assertThat(p.intents.get(0).callFlags, is((int) CallFlags.ALL.getValue()));

        p = gov.getProposal(id);
        assertThat(p.intents.size(), is(1));
        assertThat(p.intents.get(0).callFlags, is((int) CallFlags.ALL.getValue()));

        List<NeoApplicationLog.Execution.Notification> notifications = neow3j.getApplicationLog(tx).send()
                .getApplicationLog().getExecutions().get(0).getNotifications();
        List<NeoApplicationLog.Execution.Notification> migratedEvents = notifications.stream()
                .filter(n -> n.getEventName().equals("ProposalMigrated")).collect(Collectors.toList());
        assertThat(migratedEvents.size(), is(3));
        assertThat(migratedEvents.get(0).getState().getList().get(0).getInteger().intValue(), is(0));
        assertThat(migratedEvents.get(1).getState().getList().get(0).getInteger().intValue(), is(1));
        assertThat(migratedEvents.get(2).getState().getList().get(0).getInteger().intValue(), is(2));
    }
}
