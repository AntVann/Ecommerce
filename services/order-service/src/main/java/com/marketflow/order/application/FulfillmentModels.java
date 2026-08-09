package com.marketflow.order.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FulfillmentModels {
    private FulfillmentModels() {}

    public record ShipmentLineRequest(UUID orderItemId, int quantity) {}

    public record CreateShipmentCommand(
            UUID orderId,
            UUID sellerId,
            String carrier,
            String trackingNumber,
            List<ShipmentLineRequest> lines) {}

    public record ShipmentLineView(UUID orderItemId, UUID variantId, int quantity) {}

    public record ShipmentView(
            UUID id,
            UUID orderId,
            UUID sellerId,
            String status,
            String carrier,
            String trackingNumber,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<ShipmentLineView> lines) {}
}
