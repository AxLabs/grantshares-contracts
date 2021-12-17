package com.axlabs.neo.grantshares;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;

@Permission(contract = "*", methods = "transfer")
public class GrantSharesTreasury {

    static final String OWNER_KEY = "owner";
    static final String FUNDERS_PREFIX = "funders";
    static final String FUNDER_COUNT_KEY = "funderCount";
    static final StorageContext ctx = Storage.getStorageContext();
    static final StorageMap fundersEnumerated = ctx.createMap(FUNDERS_PREFIX);

    @OnDeployment
    public static void deploy(Object data, boolean update) {
        if (!update) {
            Object[] ownerAndDonors = (Object[]) data;
            Storage.put(ctx, OWNER_KEY, (Hash160) ownerAndDonors[0]);
            int count = 0;
            for (Hash160 donor : (Hash160[]) ownerAndDonors[1]) {
                fundersEnumerated.put(count, donor);
                count++;
            }
            Storage.put(ctx, FUNDER_COUNT_KEY, count);
        }
    }

    @Safe
    @OnNEP17Payment
    public static void onNep17Payment(Hash160 sender, int amount, Object data) {
        assert isWhitelisted(sender) : "GrantSharesTreasury: Payment from non-whitelisted sender";
    }

    private static boolean isWhitelisted(Hash160 sender) {
        // TODO: Consider adding a second StorageMap with the funder hash as keys for easier
        //  existence checks.
        Iterator<Hash160> it = Storage.find(ctx, FUNDERS_PREFIX, FindOptions.ValuesOnly);
        while (it.next()) {
            if (sender == it.get()) {
                return true;
            }
        }
        return false;
    }

    @Safe
    public static Paginator.Paginated getFunders(int page, int itemsPerPage) {
        int n = Storage.getInteger(ctx, FUNDER_COUNT_KEY);
        return Paginator.paginate(n, page, itemsPerPage, fundersEnumerated);
    }

    public static void addFunder(Hash160 funder) {
        assertCallerIsOwner();
        assert !isWhitelisted(funder) : "GrantSharesTreasury: Already a funder";
        int n = Storage.getInteger(ctx, FUNDER_COUNT_KEY);
        fundersEnumerated.put(n, funder);
        Storage.put(ctx, FUNDER_COUNT_KEY, n+1);
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

//    @OnNEP11Payment
//    public static void onNep11Payment(Hash160 sender, int amount, ByteString tokenId, Object data) {
//        assert isWhitelisted(sender) : "GrantSharesTreasury: Payment from non-whitelisted sender";
//    }

//    public static void releaseNFT(Hash160 tokenContract, Hash160 to, ByteString tokenId) {
//        assertCallerIsOwner();
//        Object[] params = new Object[]{to, tokenId, new Object[]{}};
//        boolean result = (boolean) Contract.call(tokenContract, "transfer",
//                (byte) (CallFlags.States | CallFlags.AllowNotify), params);
//        assert result : "GrantSharesTreasury: Releasing NFT failed";
//    }

}
