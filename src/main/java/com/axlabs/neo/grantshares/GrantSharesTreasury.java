package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.ECPoint;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event1Arg;

import static io.neow3j.devpack.Account.createStandardAccount;
import static io.neow3j.devpack.constants.FindOptions.ValuesOnly;

@Permission(contract = "*", methods = "transfer")
@SuppressWarnings("unchecked")
public class GrantSharesTreasury {

    static final String OWNER_KEY = "owner";
    static final String FUNDERS_PREFIX = "funders";
    static final String FUNDER_COUNT_KEY = "funderCount";

    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap funders = ctx.createMap(FUNDERS_PREFIX);

    @DisplayName("FunderAdded")
    static Event1Arg<Hash160> funderAdded;

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] ownerAndFunders = (Object[]) data;

            // Set owner
            Storage.put(ctx, OWNER_KEY, (Hash160) ownerAndFunders[0]);

            // Set initial funders.
            ECPoint[] pubKeys = (ECPoint[]) ownerAndFunders[1];
            for (ECPoint pubKey : pubKeys) {
                funders.put(createStandardAccount(pubKey).toByteString(), pubKey.toByteString());
            }
            Storage.put(ctx, FUNDER_COUNT_KEY, pubKeys.length);
        }
    }

    @Safe
    @OnNEP17Payment
    public static void onNep17Payment(Hash160 sender, int amount, Object data) {
        assert funders.get(sender.toByteString()) != null :
                "GrantSharesTreasury: Payment from non-whitelisted sender";
    }

    @Safe
    public static int getFundersCount() {
        return Storage.getInt(ctx, FUNDER_COUNT_KEY);
    }

    @Safe
    public static List<ECPoint> getFunders() {
        Iterator<ByteString> it = Storage.find(ctx, FUNDERS_PREFIX, ValuesOnly);
        List<ECPoint> funders = new List<>();
        while (it.next()) {
            funders.add(new ECPoint(it.get()));
        }
        return funders;
    }

    public static void addFunder(ECPoint funderKey) {
        assertCallerIsOwner();
        Hash160 funderHash = createStandardAccount(funderKey);
        assert funders.get(funderHash.toByteString()) == null: "GrantSharesTreasury: Already a funder";
        funders.put(funderHash.toByteString(), funderKey.toByteString());
        Storage.put(ctx, FUNDER_COUNT_KEY, Storage.getInt(ctx, FUNDER_COUNT_KEY) + 1);
        funderAdded.fire(funderHash);
    }

    public static void releaseTokens(Hash160 tokenContract, Hash160 to, int amount) {
        assertCallerIsOwner();
        Object[] params = new Object[]{
                Runtime.getExecutingScriptHash(), to, amount, new Object[]{}};
        boolean result = (boolean) Contract.call(tokenContract, "transfer",
                (byte) (CallFlags.States | CallFlags.AllowNotify), params);
        assert result : "GrantSharesTreasury: Releasing tokens failed: " + StdLib.itoa(amount, 10)
                + " from " + tokenContract.toByteString().toString();
    }

    public static void update(ByteString nef, String manifest, Object data) {
        assertCallerIsOwner();
        ContractManagement.update(nef, manifest, data);
    }

    private static void assertCallerIsOwner() {
        assert Runtime.getCallingScriptHash().toByteString() == Storage.get(ctx, OWNER_KEY);
    }

}
