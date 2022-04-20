package com.axlabs.neo.grantshares.util;

import io.neow3j.protocol.core.stackitem.StackItem;

import java.util.List;
import java.util.stream.Collectors;

public class ProposalPaginatedStruct {

    public int page;
    public int pages;
    public List<ProposalStruct> items;

    public ProposalPaginatedStruct(List<StackItem> list) {
        this(
                list.get(0).getInteger().intValue(),
                list.get(1).getInteger().intValue(),
                list.get(2).getList().stream().map(i -> new ProposalStruct(i.getList()))
                        .collect(Collectors.toList())
        );
    }

    public ProposalPaginatedStruct(int page, int pages, List<ProposalStruct> items) {
        this.page = page;
        this.pages = pages;
        this.items = items;
    }
}
