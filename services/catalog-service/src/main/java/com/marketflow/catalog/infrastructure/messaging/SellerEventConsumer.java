package com.marketflow.catalog.infrastructure.messaging;

import com.marketflow.catalog.application.CatalogRepository;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class SellerEventConsumer {
    private final CatalogRepository repository;
    private final ObjectMapper mapper;

    public SellerEventConsumer(CatalogRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "marketflow.seller.events.v1", groupId = "catalog-seller-v1")
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode event = mapper.readTree(payload);
        String type = event.get("eventType").asText();
        String status =
                switch (type) {
                    case "seller.seller-approved.v1" -> "APPROVED";
                    case "seller.seller-rejected.v1" -> "REJECTED";
                    case "seller.seller-suspended.v1" -> "SUSPENDED";
                    default -> null;
                };
        if (status != null) {
            repository.applySellerStatus(
                    UUID.fromString(event.get("eventId").asText()),
                    UUID.fromString(event.get("aggregateId").asText()),
                    status,
                    event.get("aggregateVersion").asLong());
        }
    }
}
