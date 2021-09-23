package com.axlabs.neo.grantshares;

import static io.neow3j.crypto.Base64.decode;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.neow3j.contract.SmartContract;
import io.neow3j.crypto.Base64;
import io.neow3j.devpack.ByteString;
import io.neow3j.protocol.Neow3jExpress;
import io.neow3j.protocol.core.response.NeoExpressGetContractStorage;
import io.neow3j.protocol.core.response.NeoGetStorage;
import io.neow3j.protocol.core.stackitem.ByteStringStackItem;
import io.neow3j.test.ContractTest;
import io.neow3j.test.ContractTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@ContractTest(
        blockTime = 1,
        contractClass = GrantSharesGov.class,
        neoxpConfig = "example.neo-express"
)
public class GrantSharesGovTest {

    @RegisterExtension
    private static ContractTestExtension ext = new ContractTestExtension();

    private Neow3jExpress neow3j;

    private SmartContract contract;

    public GrantSharesGovTest(Neow3jExpress neow3j, SmartContract contract) {
        this.neow3j = neow3j;
        this.contract = contract;
    }

    @Test
    public void deploy() throws Throwable {
        NeoGetStorage result = neow3j.getStorage(contract.getScriptHash(), "0101").send();
        assertThat(new String(decode(result.getStorage())), is("deployed!"));
    }

}
