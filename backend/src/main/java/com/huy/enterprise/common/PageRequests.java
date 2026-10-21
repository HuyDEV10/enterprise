package com.huy.enterprise.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.util.Set;

public final class PageRequests {
    private PageRequests() {}

    public static Pageable validate(Pageable request, String... allowed) {
        Set<String> fields = Set.of(allowed);
        for (Sort.Order order : request.getSort()) {
            if (!fields.contains(order.getProperty())) throw new IllegalArgumentException("Unsupported sort field: " + order.getProperty());
        }
        if (request.getOffset() > Integer.MAX_VALUE) throw new IllegalArgumentException("Requested page is too large");
        Sort sort = request.getSort();
        if (sort.getOrderFor("id") == null) sort = sort.and(Sort.by("id"));
        return PageRequest.of(request.getPageNumber(), Math.min(request.getPageSize(), 100), sort);
    }
}
