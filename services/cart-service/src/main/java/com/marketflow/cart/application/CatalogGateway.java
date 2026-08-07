package com.marketflow.cart.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface CatalogGateway {
    VariantQuote quote(UUID variantId);

    record VariantQuote(
            UUID productId,
            UUID variantId,
            UUID sellerId,
            boolean active,
            BigDecimal amount,
            String currency,
            long productVersion,
            String reason) {}
}
