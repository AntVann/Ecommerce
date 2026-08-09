package com.marketflow.order.api;

import com.marketflow.order.application.CheckoutModels;
import com.marketflow.order.application.CheckoutModels.CheckoutCommand;
import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.application.CheckoutService;
import com.marketflow.order.application.FulfillmentModels;
import com.marketflow.order.application.FulfillmentService;
import com.marketflow.order.application.OrderQueryService;
import com.marketflow.order.application.PaymentAuthorizationService;
import com.marketflow.order.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final CheckoutService checkout;
    private final PaymentAuthorizationService payments;
    private final OrderQueryService queries;
    private final FulfillmentService fulfillment;

    public OrderController(
            CheckoutService checkout,
            PaymentAuthorizationService payments,
            OrderQueryService queries,
            FulfillmentService fulfillment) {
        this.checkout = checkout;
        this.payments = payments;
        this.queries = queries;
        this.fulfillment = fulfillment;
    }

    @PostMapping("/api/v1/checkouts")
    @PreAuthorize("hasRole('CUSTOMER')")
    ResponseEntity<OrderView> checkout(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String key,
            @Valid @RequestBody CheckoutRequest r) {
        OrderView order =
                checkout.checkout(
                        UUID.fromString(jwt.getSubject()),
                        key,
                        new CheckoutCommand(
                                r.cartId(),
                                r.cartVersion(),
                                r.shippingAddress(),
                                r.billingAddress()),
                        correlation());
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/orders/" + order.id()))
                .body(order);
    }

    @GetMapping("/api/v1/orders/{orderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    OrderView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return checkout.get(UUID.fromString(jwt.getSubject()), orderId);
    }

    @GetMapping("/api/v1/orders/{orderId}/history")
    @PreAuthorize("hasRole('CUSTOMER')")
    java.util.List<CheckoutModels.StatusHistory> history(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return checkout.history(UUID.fromString(jwt.getSubject()), orderId);
    }

    @GetMapping("/api/v1/orders")
    @PreAuthorize("hasRole('CUSTOMER')")
    CheckoutModels.OrderPage history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") @Min(1) int limit) {
        return queries.customerHistory(UUID.fromString(jwt.getSubject()), cursor, limit);
    }

    @GetMapping("/api/v1/orders/{orderId}/shipments")
    @PreAuthorize("hasRole('CUSTOMER')")
    java.util.List<FulfillmentModels.ShipmentView> shipments(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return fulfillment.customerShipments(UUID.fromString(jwt.getSubject()), orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/payment-authorizations")
    @PreAuthorize("hasRole('CUSTOMER')")
    ResponseEntity<OrderView> authorizePayment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String key,
            @Valid @RequestBody PaymentAuthorizationRequest request) {
        return ResponseEntity.accepted()
                .body(
                        payments.authorize(
                                UUID.fromString(jwt.getSubject()),
                                orderId,
                                key,
                                request.fakePaymentToken(),
                                correlation()));
    }

    @GetMapping("/api/v1/sellers/{sellerId}/orders")
    CheckoutModels.SellerOrderPage sellerHistory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "25") @Min(1) int limit) {
        return queries.sellerHistory(UUID.fromString(jwt.getSubject()), sellerId, cursor, limit);
    }

    @GetMapping("/api/v1/sellers/{sellerId}/orders/{orderId}")
    CheckoutModels.SellerOrderView sellerOrder(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID orderId) {
        return queries.sellerOrder(UUID.fromString(jwt.getSubject()), sellerId, orderId);
    }

    @PostMapping("/api/v1/sellers/{sellerId}/orders/{orderId}/shipments")
    @PreAuthorize("isAuthenticated()")
    ResponseEntity<FulfillmentModels.ShipmentView> createShipment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID orderId,
            @RequestHeader("Idempotency-Key") @Size(min = 16, max = 128) String key,
            @Valid @RequestBody ShipmentRequest request) {
        var lines =
                request.lines().stream()
                        .map(
                                line ->
                                        new FulfillmentModels.ShipmentLineRequest(
                                                line.orderItemId(), line.quantity()))
                        .toList();
        var shipment =
                fulfillment.create(
                        UUID.fromString(jwt.getSubject()),
                        sellerId,
                        orderId,
                        key,
                        request.carrier(),
                        request.trackingNumber(),
                        lines,
                        correlation());
        return ResponseEntity.created(URI.create("/api/v1/shipments/" + shipment.id()))
                .body(shipment);
    }

    @PatchMapping("/api/v1/sellers/{sellerId}/shipments/{shipmentId}")
    @PreAuthorize("isAuthenticated()")
    FulfillmentModels.ShipmentView transitionShipment(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sellerId,
            @PathVariable UUID shipmentId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ShipmentStatusRequest request) {
        return fulfillment.transition(
                UUID.fromString(jwt.getSubject()),
                sellerId,
                shipmentId,
                version(ifMatch),
                request.status(),
                correlation());
    }

    private static String correlation() {
        String c = MDC.get("correlationId");
        return c == null ? "unknown" : c;
    }

    public record CheckoutRequest(
            @NotNull UUID cartId,
            @Min(1) long cartVersion,
            @NotNull @Valid Address shippingAddress,
            @NotNull @Valid Address billingAddress) {}

    public record PaymentAuthorizationRequest(
            @NotBlank @Pattern(regexp = "^mf_fake_[a-z0-9_]{1,96}$") String fakePaymentToken) {}

    public record ShipmentRequest(
            @NotBlank @Size(min = 2, max = 80) String carrier,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9-]{3,120}$") String trackingNumber,
            @NotNull @Size(min = 1, max = 50) List<@Valid ShipmentLineRequest> lines) {}

    public record ShipmentLineRequest(@NotNull UUID orderItemId, @Min(1) @Max(99) int quantity) {}

    public record ShipmentStatusRequest(
            @NotBlank @Pattern(regexp = "IN_TRANSIT|DELIVERED") String status) {}

    private static long version(String value) {
        try {
            return Long.parseLong(value.replace("W/", "").replace("\"", ""));
        } catch (NumberFormatException e) {
            throw new com.marketflow.order.api.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "INVALID_ETAG_400",
                    "If-Match must contain a valid resource version.");
        }
    }
}
