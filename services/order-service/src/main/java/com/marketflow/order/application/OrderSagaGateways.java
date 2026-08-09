package com.marketflow.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public interface OrderSagaGateways {
    void authorizePayment(
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            String fakePaymentToken,
            String idempotencyKey);

    void confirmInventory(UUID orderId);

    void releaseInventory(UUID orderId);

    void requireSellerPermission(UUID sellerId, UUID userId);

    void requireFulfillmentPermission(UUID sellerId, UUID userId);
}
