package com.axlabs.neo.grantshares;

import io.neow3j.devpack.List;

/**
 * Utility for paging through storage entries.
 */
public class Paginator {

    /**
     * Calculates the start and end indices of a page in the list of {@code n} items.
     *
     * @param n            The total number of available items.
     * @param page         The desired page.
     * @param itemsPerPage The desired number of items per page.
     * @return The start and end index of items on the desired page, plus the total number of pages available given
     * that there are {@code n} items.
     */
    static int[] calcPagination(int n, int page, int itemsPerPage) throws Exception {
        int pages;
        if (n < itemsPerPage) {
            pages = 1;
        } else if (n % itemsPerPage == 0) {
            pages = n / itemsPerPage;
        } else {
            pages = (n / itemsPerPage) + 1;
        }
        if (page >= pages) throw new Exception("[Paginator.calcPagination] Page out of bounds");
        int startAt = itemsPerPage * page;
        int endAt = startAt + itemsPerPage;
        if (startAt + itemsPerPage > n) {
            endAt = n;
        }
        return new int[]{startAt, endAt, pages};
    }

    /**
     * Struct used to return a page in a set of items.
     * <p>
     * Instead of just returning the items of a page, this struct gives some context information, i.e., the page
     * number and the total number of pages available.
     */
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
