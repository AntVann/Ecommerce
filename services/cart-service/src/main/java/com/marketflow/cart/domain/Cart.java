package com.marketflow.cart.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record Cart(
        UUID cartId,
        ActorType actorType,
        String actorKey,
        long version,
        Map<UUID, CartItem> items,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {
    public Cart {
        items = Map.copyOf(new LinkedHashMap<>(items));
    }

    public enum ActorType {
        GUEST,
        CUSTOMER
    }

    public record CartItem(
            UUID productId,
            UUID variantId,
            int quantity,
            BigDecimal estimatedUnitPrice,
            String currency,
            Instant estimatedAt,
            ValidityStatus validityStatus,
            String validityReason) {}

    public enum ValidityStatus {
        VALID,
        PRODUCT_INACTIVE,
        VARIANT_INACTIVE,
        SELLER_INACTIVE,
        NOT_FOUND,
        PRICE_CHANGED,
        VALIDATION_UNAVAILABLE
    }
}
