package com.marketflow.order.api;

import com.marketflow.order.application.CheckoutModels;
import com.marketflow.order.application.CheckoutModels.CheckoutCommand;
import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.application.CheckoutService;
import com.marketflow.order.application.OrderQueryService;
import com.marketflow.order.application.PaymentAuthorizationService;
import com.marketflow.order.domain.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

    public OrderController(
            CheckoutService checkout,
            PaymentAuthorizationService payments,
            OrderQueryService queries) {
        this.checkout = checkout;
        this.payments = payments;
        this.queries = queries;
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
}
