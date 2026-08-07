package com.marketflow.cart.api;

import com.marketflow.cart.application.CartService;
import com.marketflow.cart.application.CartService.Actor;
import com.marketflow.cart.domain.Cart;
import com.marketflow.cart.domain.Cart.ActorType;
import com.marketflow.cart.infrastructure.CartProperties;
import com.marketflow.cart.infrastructure.security.GuestCsrfFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CartController {
    private static final String GUEST_COOKIE = "MARKETFLOW_GUEST_CART";
    private final CartService carts;
    private final CartProperties properties;
    private final SecureRandom random = new SecureRandom();

    public CartController(CartService carts, CartProperties properties) {
        this.carts = carts;
        this.properties = properties;
    }

    @GetMapping("/api/v1/cart")
    ResponseEntity<CartResponse> get(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        GuestContext guest = jwt == null ? guest(request) : null;
        Cart cart = carts.get(actor(jwt, guest));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok().eTag(etag(cart.version()));
        if (guest != null && guest.created()) {
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookie(GUEST_COOKIE, guest.token(), true, properties.guestTtl()).toString());
            response.header(
                    HttpHeaders.SET_COOKIE,
                    cookie(GuestCsrfFilter.COOKIE, token(), false, properties.guestTtl())
                            .toString());
        }
        return response.body(view(cart));
    }

    @PostMapping("/api/v1/cart/items")
    ResponseEntity<CartResponse> add(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @Valid @RequestBody ItemRequest body) {
        return response(carts.add(actor(jwt, guest(request)), body.variantId(), body.quantity()));
    }

    @PatchMapping("/api/v1/cart/items/{variantId}")
    ResponseEntity<CartResponse> update(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @PathVariable UUID variantId,
            @RequestHeader("If-Match") String match,
            @Valid @RequestBody QuantityRequest body) {
        return response(
                carts.update(
                        actor(jwt, guest(request)), variantId, body.quantity(), version(match)));
    }

    @DeleteMapping("/api/v1/cart/items/{variantId}")
    ResponseEntity<CartResponse> remove(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @PathVariable UUID variantId,
            @RequestHeader("If-Match") String match) {
        return response(carts.remove(actor(jwt, guest(request)), variantId, version(match)));
    }

    @DeleteMapping("/api/v1/cart")
    ResponseEntity<Void> clear(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestHeader("If-Match") String match) {
        carts.clear(actor(jwt, guest(request)), version(match));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/v1/cart/merge")
    ResponseEntity<CartResponse> merge(
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        if (jwt == null) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "AUTHENTICATION_REQUIRED_401",
                    "Authentication is required.");
        }
        String raw = cookieValue(request, GUEST_COOKIE);
        Cart cart =
                carts.merge(
                        digest(raw == null ? "marketflow:no-guest-cart" : raw),
                        UUID.fromString(jwt.getSubject()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired(GUEST_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, expired(GuestCsrfFilter.COOKIE).toString())
                .eTag(etag(cart.version()))
                .body(view(cart));
    }

    private ResponseEntity<CartResponse> response(Cart cart) {
        return ResponseEntity.ok().eTag(etag(cart.version())).body(view(cart));
    }

    static CartResponse view(Cart cart) {
        List<CartItemResponse> items =
                cart.items().values().stream()
                        .sorted(Comparator.comparing(Cart.CartItem::variantId))
                        .map(
                                item ->
                                        new CartItemResponse(
                                                item.productId(),
                                                item.variantId(),
                                                item.quantity(),
                                                item.estimatedUnitPrice() == null
                                                        ? null
                                                        : new MoneyResponse(
                                                                item.estimatedUnitPrice()
                                                                        .toPlainString(),
                                                                item.currency()),
                                                item.estimatedAt(),
                                                item.validityStatus(),
                                                item.validityReason()))
                        .toList();
        return new CartResponse(
                cart.cartId(),
                cart.actorType(),
                cart.version(),
                items,
                cart.createdAt(),
                cart.updatedAt(),
                cart.expiresAt());
    }

    private Actor actor(Jwt jwt, GuestContext guest) {
        return jwt == null
                ? new Actor(ActorType.GUEST, digest(guest.token()))
                : new Actor(ActorType.CUSTOMER, UUID.fromString(jwt.getSubject()).toString());
    }

    private GuestContext guest(HttpServletRequest request) {
        String value = cookieValue(request, GUEST_COOKIE);
        return value == null ? new GuestContext(token(), true) : new GuestContext(value, false);
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String digest(String token) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies)
                .filter(c -> c.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie cookie(String name, String value, boolean httpOnly, Duration ttl) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .path("/api/v1/cart")
                .maxAge(ttl)
                .build();
    }

    private ResponseCookie expired(String name) {
        return cookie(name, "", name.equals(GUEST_COOKIE), Duration.ZERO);
    }

    private long version(String etag) {
        try {
            return Long.parseLong(etag.replace("W/", "").replace("\"", ""));
        } catch (NumberFormatException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ETAG_INVALID_400", "If-Match is invalid.");
        }
    }

    private String etag(long version) {
        return "\"" + version + "\"";
    }

    record GuestContext(String token, boolean created) {}

    record MoneyResponse(String amount, String currency) {}

    record CartItemResponse(
            UUID productId,
            UUID variantId,
            int quantity,
            MoneyResponse estimatedUnitPrice,
            Instant estimatedAt,
            Cart.ValidityStatus validityStatus,
            String validityReason) {}

    record CartResponse(
            UUID cartId,
            ActorType actorType,
            long version,
            List<CartItemResponse> items,
            Instant createdAt,
            Instant updatedAt,
            Instant expiresAt) {}

    record ItemRequest(@NotNull UUID variantId, @Min(1) @Max(99) int quantity) {}

    record QuantityRequest(@Min(1) @Max(99) int quantity) {}
}
