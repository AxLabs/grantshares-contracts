package com.axlabs.neo.grantshares;

import io.neow3j.devpack.List;
import io.neow3j.devpack.StorageMap;

public class Paginator {

    public static Paginated paginate(int n, int page, int itemsPerPage, StorageMap enumerated) {
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
        List<Object> list = new List<>();
        for (int i = startAt; i < endAt; i++) {
            list.add(enumerated.get(i));
        }
        return new Paginated(page, pages, list);
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
