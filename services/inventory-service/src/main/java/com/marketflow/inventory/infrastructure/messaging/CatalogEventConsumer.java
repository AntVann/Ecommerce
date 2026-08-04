package com.marketflow.inventory.infrastructure.messaging;

import com.marketflow.inventory.application.InventoryRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class CatalogEventConsumer {
    private final InventoryRepository repository;
    private final ObjectMapper mapper;

    public CatalogEventConsumer(InventoryRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "marketflow.catalog.events.v1", groupId = "inventory-catalog-v1")
    @Transactional
    public void consume(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        String type = root.get("eventType").asText();
        if (!type.equals("catalog.product-published.v1")
                && !type.equals("catalog.product-updated.v1")) return;
        UUID eventId = UUID.fromString(root.get("eventId").asText());
        if (repository.processed("inventory-catalog-v1", eventId)) return;
        JsonNode data = root.get("data");
        UUID sellerId = UUID.fromString(data.get("sellerId").asText());
        for (JsonNode variant : data.get("variants")) {
            repository.ensureItem(
                    UUID.fromString(variant.get("id").asText()), sellerId, Instant.now());
        }
    }
}
