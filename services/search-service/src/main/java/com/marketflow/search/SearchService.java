package com.marketflow.search;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class SearchService {
    private final RestClient openSearch;
    private final RestClient catalog;
    private final SearchProperties properties;
    private final SearchRepository repository;
    private final ObjectMapper mapper;
    private final MeterRegistry metrics;

    public SearchService(
            RestClient.Builder builder,
            SearchProperties properties,
            SearchRepository repository,
            ObjectMapper mapper,
            MeterRegistry metrics) {
        this.openSearch = builder.clone().baseUrl(properties.openSearchUrl()).build();
        this.catalog = builder.clone().baseUrl(properties.catalogBaseUrl()).build();
        this.properties = properties;
        this.repository = repository;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public JsonNode search(String query, UUID categoryId, int limit) {
        Map<String, Object> filter =
                categoryId == null
                        ? Map.of()
                        : Map.of("term", Map.of("categoryId", categoryId.toString()));
        Map<String, Object> body =
                Map.of(
                        "size",
                        Math.min(limit, 100),
                        "query",
                        Map.of(
                                "bool",
                                Map.of(
                                        "must",
                                        query == null || query.isBlank()
                                                ? List.of(Map.of("match_all", Map.of()))
                                                : List.of(
                                                        Map.of(
                                                                "multi_match",
                                                                Map.of(
                                                                        "query",
                                                                        query,
                                                                        "fields",
                                                                        List.of(
                                                                                "title^3",
                                                                                "description",
                                                                                "attributes.*")))),
                                        "filter",
                                        categoryId == null ? List.of() : List.of(filter))));
        return openSearch
                .post()
                .uri("/{alias}/_search", properties.alias())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public void project(JsonNode envelope) {
        String type = envelope.get("eventType").asText();
        UUID eventId = UUID.fromString(envelope.get("eventId").asText());
        if (repository.alreadyProcessed("search-events-v1", eventId)) return;
        if (type.startsWith("catalog.product-")) {
            JsonNode data = envelope.get("data");
            String productId = data.get("productId").asText();
            if (type.equals("catalog.product-deactivated.v1")
                    || !"ACTIVE".equals(data.path("status").asText())) {
                delete(productId);
            } else {
                UUID sellerId = UUID.fromString(data.get("sellerId").asText());
                if (repository.sellerSuspended(sellerId)) {
                    delete(productId);
                    repository.processed("search-events-v1", eventId);
                    return;
                }
                Map<String, Object> document = mapper.convertValue(data, Map.class);
                document.put("aggregateVersion", envelope.get("aggregateVersion").asLong());
                document.put("projectedAt", Instant.now().toString());
                openSearch
                        .put()
                        .uri("/{alias}/_doc/{id}", properties.alias(), productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(document)
                        .retrieve()
                        .toBodilessEntity();
            }
            repository.processed("search-events-v1", eventId);
            metrics.counter("search_projection_total", "event_type", type).increment();
        } else if (type.equals("seller.seller-suspended.v1")
                || type.equals("seller.seller-rejected.v1")) {
            String sellerId = envelope.get("aggregateId").asText();
            repository.sellerStatus(
                    UUID.fromString(sellerId),
                    type.equals("seller.seller-suspended.v1") ? "SUSPENDED" : "REJECTED",
                    envelope.get("aggregateVersion").asLong());
            openSearch
                    .post()
                    .uri("/{alias}/_delete_by_query", properties.alias())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", Map.of("term", Map.of("sellerId", sellerId))))
                    .retrieve()
                    .toBodilessEntity();
            repository.processed("search-events-v1", eventId);
        } else if (type.equals("seller.seller-approved.v1")) {
            repository.sellerStatus(
                    UUID.fromString(envelope.get("aggregateId").asText()),
                    "APPROVED",
                    envelope.get("aggregateVersion").asLong());
            repository.processed("search-events-v1", eventId);
        }
    }

    public RebuildResult rebuild() {
        String index = "marketflow-products-" + Instant.now().toEpochMilli();
        UUID job = repository.startRebuild(index);
        long indexed = 0;
        long failed = 0;
        try {
            createIndex(index);
            for (long offset = 0; ; offset += 100) {
                long currentOffset = offset;
                JsonNode page =
                        catalog.get()
                                .uri(
                                        uri ->
                                                uri.path("/internal/v1/catalog/products/export")
                                                        .queryParam("offset", currentOffset)
                                                        .queryParam("limit", 100)
                                                        .build())
                                .header("X-Internal-Service-Key", properties.internalServiceKey())
                                .retrieve()
                                .body(JsonNode.class);
                if (page == null || !page.isArray() || page.isEmpty()) break;
                for (JsonNode view : page) {
                    try {
                        JsonNode product = view.get("product");
                        Map<String, Object> doc =
                                new java.util.HashMap<>(mapper.convertValue(product, Map.class));
                        doc.remove("attributesJson");
                        doc.put("variants", mapper.convertValue(view.get("variants"), List.class));
                        doc.put("images", mapper.convertValue(view.get("images"), List.class));
                        doc.put(
                                "attributes",
                                mapper.convertValue(view.get("attributes"), Map.class));
                        openSearch
                                .put()
                                .uri("/{index}/_doc/{id}", index, product.get("id").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(doc)
                                .retrieve()
                                .toBodilessEntity();
                        indexed++;
                    } catch (RuntimeException exception) {
                        failed++;
                    }
                }
                if (page.size() < 100) break;
            }
            switchAlias(index);
            repository.finishRebuild(
                    job, indexed, failed, failed == 0 ? "COMPLETED" : "COMPLETED_WITH_ERRORS");
            metrics.counter("search_rebuild_total", "outcome", failed == 0 ? "success" : "partial")
                    .increment();
            return new RebuildResult(job, index, indexed, failed);
        } catch (RuntimeException exception) {
            repository.finishRebuild(job, indexed, failed + 1, "FAILED");
            throw exception;
        }
    }

    public void ensureAlias() {
        try {
            openSearch.head().uri("/{alias}", properties.alias()).retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            String index = "marketflow-products-bootstrap-" + Instant.now().toEpochMilli();
            createIndex(index);
            switchAlias(index);
        }
    }

    private void createIndex(String index) {
        Map<String, Object> mapping =
                Map.of(
                        "mappings",
                        Map.of(
                                "dynamic",
                                "strict",
                                "properties",
                                Map.ofEntries(
                                        Map.entry("productId", Map.of("type", "keyword")),
                                        Map.entry("id", Map.of("type", "keyword")),
                                        Map.entry("sellerId", Map.of("type", "keyword")),
                                        Map.entry("categoryId", Map.of("type", "keyword")),
                                        Map.entry(
                                                "title",
                                                Map.of(
                                                        "type",
                                                        "text",
                                                        "fields",
                                                        Map.of(
                                                                "keyword",
                                                                Map.of("type", "keyword")))),
                                        Map.entry("description", Map.of("type", "text")),
                                        Map.entry("status", Map.of("type", "keyword")),
                                        Map.entry(
                                                "attributes",
                                                Map.of("type", "object", "dynamic", true)),
                                        Map.entry(
                                                "variants",
                                                Map.of("type", "nested", "dynamic", true)),
                                        Map.entry(
                                                "images",
                                                Map.of("type", "nested", "dynamic", true)),
                                        Map.entry("aggregateVersion", Map.of("type", "long")),
                                        Map.entry("projectedAt", Map.of("type", "date")),
                                        Map.entry("version", Map.of("type", "long")),
                                        Map.entry("createdAt", Map.of("type", "date")),
                                        Map.entry("updatedAt", Map.of("type", "date")),
                                        Map.entry("publishedAt", Map.of("type", "date")))));
        openSearch
                .put()
                .uri("/{index}", index)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapping)
                .retrieve()
                .toBodilessEntity();
    }

    private void switchAlias(String index) {
        List<Map<String, Object>> actions = new ArrayList<>();
        try {
            JsonNode aliases =
                    openSearch
                            .get()
                            .uri("/_alias/{alias}", properties.alias())
                            .retrieve()
                            .body(JsonNode.class);
            if (aliases != null && aliases.isObject())
                aliases.propertyNames()
                        .forEach(
                                old ->
                                        actions.add(
                                                Map.of(
                                                        "remove",
                                                        Map.of(
                                                                "index",
                                                                old,
                                                                "alias",
                                                                properties.alias()))));
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() != 404) throw exception;
        }
        actions.add(Map.of("add", Map.of("index", index, "alias", properties.alias())));
        openSearch
                .post()
                .uri("/_aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("actions", actions))
                .retrieve()
                .toBodilessEntity();
    }

    private void delete(String id) {
        openSearch
                .delete()
                .uri("/{alias}/_doc/{id}", properties.alias(), id)
                .retrieve()
                .onStatus(status -> status.value() == 404, (request, response) -> {})
                .toBodilessEntity();
    }

    public record RebuildResult(UUID jobId, String indexName, long indexed, long failed) {}
}
