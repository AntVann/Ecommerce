package com.marketflow.cart.api;

import com.marketflow.cart.application.CartService;
import com.marketflow.cart.domain.Cart;
import com.marketflow.cart.infrastructure.CartProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class InternalCartController {
    private final CartService carts;
    private final CartProperties properties;

    public InternalCartController(CartService carts, CartProperties properties) {
        this.carts = carts;
        this.properties = properties;
    }

    @PostMapping("/internal/v1/carts/checkout-snapshots")
    CartSnapshot snapshot(
            @RequestHeader(name = "X-Internal-Service-Key", required = false) String key,
            @Valid @RequestBody SnapshotRequest request) {
        if (!same(key, properties.internalServiceKey())) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "INTERNAL_AUTHENTICATION_401",
                    "Internal authentication failed.");
        }
        Cart cart =
                carts.checkoutSnapshot(
                        request.customerId(), request.cartId(), request.cartVersion());
        return new CartSnapshot(
                cart.cartId(),
                cart.version(),
                cart.items().values().stream()
                        .map(
                                item ->
                                        new CartLine(
                                                item.productId(),
                                                item.variantId(),
                                                item.quantity()))
                        .toList());
    }

    private boolean same(String supplied, String expected) {
        byte[] left = supplied == null ? new byte[0] : supplied.getBytes(StandardCharsets.UTF_8);
        byte[] right = expected == null ? new byte[0] : expected.getBytes(StandardCharsets.UTF_8);
        return supplied != null && expected != null && MessageDigest.isEqual(left, right);
    }

    record SnapshotRequest(@NotNull UUID customerId, @NotNull UUID cartId, long cartVersion) {}

    record CartSnapshot(UUID cartId, long version, List<CartLine> items) {}

    record CartLine(UUID productId, UUID variantId, int quantity) {}
}
