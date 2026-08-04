package com.marketflow.search;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public final class SearchIndexInitializer {
    private final SearchService search;

    public SearchIndexInitializer(SearchService search) {
        this.search = search;
    }

    @EventListener(ApplicationReadyEvent.class)
    void ready() {
        search.ensureAlias();
    }
}
