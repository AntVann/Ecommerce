package com.marketflow.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
public final class SearchController {
    private final SearchService search;
    private final SearchProperties properties;

    public SearchController(SearchService search, SearchProperties properties) {
        this.search = search;
        this.properties = properties;
    }

    @GetMapping("/api/v1/products")
    JsonNode search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "25") int limit) {
        return search.search(q, categoryId, limit);
    }

    @PostMapping("/internal/v1/search/rebuild")
    SearchService.RebuildResult rebuild(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key) {
        requireKey(key);
        return search.rebuild();
    }

    private void requireKey(String supplied) {
        byte[] expected = properties.internalServiceKey().getBytes(StandardCharsets.UTF_8);
        byte[] actual = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual))
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED);
    }
}
