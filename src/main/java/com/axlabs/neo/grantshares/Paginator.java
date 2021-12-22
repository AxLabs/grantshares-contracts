package com.axlabs.neo.grantshares;

import io.neow3j.devpack.List;
import io.neow3j.devpack.StorageMap;

public class Paginator {

    static int[] calcPagination(int n, int page, int itemsPerPage) {
        int pages;
        if (n < itemsPerPage) {
            pages = 1;
        } else if (n % itemsPerPage == 0) {
            pages = n / itemsPerPage;
        } else {
            pages = (n / itemsPerPage) + 1;
        }
        assert page < pages : "GrantSharesGov: Page out of bounds";
        int startAt = itemsPerPage * page;
        int endAt = startAt + itemsPerPage;
        if (startAt + itemsPerPage > n) {
            endAt = n;
        }
        return new int[]{startAt, endAt, pages};
    }


    static class Paginated {
        public int page;
        public int pages;
        public List<Object> items;

        public Paginated(int page, int pages, List<Object> items) {
            this.page = page;
            this.pages = pages;
            this.items = items;
        }
    }
}
