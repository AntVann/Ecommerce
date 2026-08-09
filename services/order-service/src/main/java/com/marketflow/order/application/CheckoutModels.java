package com.marketflow.order.application;

import com.marketflow.order.domain.Address;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CheckoutModels {
    private CheckoutModels() {}

    public record CheckoutCommand(
            UUID cartId, long cartVersion, Address shippingAddress, Address billingAddress) {}

    public record CartSnapshot(UUID cartId, long version, List<CartLine> items) {}

    public record CartLine(UUID productId, UUID variantId, int quantity) {}

    public record CatalogLine(
            UUID productId,
            UUID variantId,
            UUID sellerId,
            String productName,
            String variantName,
            String sku,
            BigDecimal priceAmount,
            String currency,
            long catalogVersion,
            boolean valid,
            String reason) {}

    public record Availability(UUID variantId, int available) {}

    public record OrderItem(
            UUID id,
            UUID sellerId,
            UUID productId,
            UUID variantId,
            String productName,
            String variantName,
            String sku,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            BigDecimal lineSubtotal,
            long catalogVersion) {}

    public record OrderView(
            UUID id,
            UUID customerId,
            UUID cartId,
            long cartVersion,
            String status,
            String cancellationReason,
            String currency,
            BigDecimal subtotal,
            BigDecimal taxTotal,
            BigDecimal shippingTotal,
            BigDecimal discountTotal,
            BigDecimal grandTotal,
            Address shippingAddress,
            Address billingAddress,
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<OrderItem> items,
            UUID paymentId,
            String paymentState,
            List<FulfillmentModels.ShipmentView> shipments) {
        public OrderView(
                UUID id,
                UUID customerId,
                UUID cartId,
                long cartVersion,
                String status,
                String cancellationReason,
                String currency,
                BigDecimal subtotal,
                BigDecimal taxTotal,
                BigDecimal shippingTotal,
                BigDecimal discountTotal,
                BigDecimal grandTotal,
                Address shippingAddress,
                Address billingAddress,
                long version,
                Instant createdAt,
                Instant updatedAt,
                List<OrderItem> items,
                UUID paymentId,
                String paymentState) {
            this(
                    id,
                    customerId,
                    cartId,
                    cartVersion,
                    status,
                    cancellationReason,
                    currency,
                    subtotal,
                    taxTotal,
                    shippingTotal,
                    discountTotal,
                    grandTotal,
                    shippingAddress,
                    billingAddress,
                    version,
                    createdAt,
                    updatedAt,
                    items,
                    paymentId,
                    paymentState,
                    List.of());
        }
    }

    public record OrderPage(List<OrderView> items, String nextCursor) {}

    public record SellerOrderView(
            UUID id,
            String status,
            String currency,
            BigDecimal sellerSubtotal,
            Instant createdAt,
            Instant updatedAt,
            List<OrderItem> items) {}

    public record SellerOrderPage(List<SellerOrderView> items, String nextCursor) {}

    public record StatusHistory(
            String previousStatus, String newStatus, String reason, Instant occurredAt) {}
}
