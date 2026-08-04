package com.marketflow.search;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SearchEventConsumer {
    private final SearchService search;
    private final ObjectMapper mapper;

    public SearchEventConsumer(SearchService search, ObjectMapper mapper) {
        this.search = search;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = {"marketflow.catalog.events.v1", "marketflow.seller.events.v1"},
            groupId = "search-events-v1")
    public void consume(String payload) throws Exception {
        search.project(mapper.readTree(payload));
    }
}
