package com.palantir.util.paging;

import java.util.List;

public interface PageGetter<T> {
    List<T> getFirstPage();

    List<T> getNextPage(List<T> currentPage);

    int getPageSize();
}
