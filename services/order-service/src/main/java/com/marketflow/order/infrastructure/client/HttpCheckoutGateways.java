package com.marketflow.order.infrastructure.client;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.CheckoutGateways;
import com.marketflow.order.application.CheckoutModels.Availability;
import com.marketflow.order.application.CheckoutModels.CartLine;
import com.marketflow.order.application.CheckoutModels.CartSnapshot;
import com.marketflow.order.application.CheckoutModels.CatalogLine;
import com.marketflow.order.infrastructure.security.OrderProperties;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpCheckoutGateways implements CheckoutGateways {
    private final OrderProperties p;
    private final RestClient.Builder builder;

    public HttpCheckoutGateways(OrderProperties p, RestClient.Builder builder) {
        this.p = p;
        this.builder = builder;
    }

    public void requireActiveCustomer(UUID id) {
        try {
            UserSummary u =
                    builder.baseUrl(p.identityBaseUrl())
                            .build()
                            .get()
                            .uri("/internal/v1/users/{id}", id)
                            .header("X-Internal-Service-Key", p.internalServiceKey())
                            .retrieve()
                            .body(UserSummary.class);
            if (u == null || !u.active() || u.roles() == null || !u.roles().contains("CUSTOMER"))
                forbidden("Customer account is not active.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            unavailable("Identity validation is unavailable.");
        }
    }

    public CartSnapshot cart(UUID customer, UUID cart, long version) {
        try {
            CartSnapshot v =
                    builder.baseUrl(p.cartBaseUrl())
                            .build()
                            .post()
                            .uri("/internal/v1/carts/checkout-snapshots")
                            .header("X-Internal-Service-Key", p.internalServiceKey())
                            .body(new CartRequest(customer, cart, version))
                            .retrieve()
                            .body(CartSnapshot.class);
            if (v == null) unavailable("Cart validation returned no response.");
            return v;
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            unavailable("Cart validation is unavailable.");
            return null;
        }
    }

    public List<CatalogLine> catalog(List<CartLine> lines) {
        try {
            List<RawCatalogLine> response =
                    builder.baseUrl(p.catalogBaseUrl())
                            .build()
                            .post()
                            .uri("/internal/v1/catalog/checkout-validations")
                            .header("X-Internal-Service-Key", p.internalServiceKey())
                            .body(
                                    Map.of(
                                            "variantIds",
                                            lines.stream().map(CartLine::variantId).toList()))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<RawCatalogLine>>() {});
            if (response == null) unavailable("Catalog validation returned no response.");
            return response.stream()
                    .map(
                            value ->
                                    new CatalogLine(
                                            value.productId(),
                                            value.variantId(),
                                            value.sellerId(),
                                            value.productName(),
                                            value.variantName(),
                                            value.sku(),
                                            value.priceAmount() == null
                                                    ? null
                                                    : new BigDecimal(value.priceAmount()),
                                            value.priceCurrency(),
                                            value.productVersion() == null
                                                    ? 0
                                                    : value.productVersion(),
                                            "VALID".equals(value.status()),
                                            value.status()))
                    .toList();
        } catch (RuntimeException e) {
            unavailable("Catalog validation is unavailable.");
            return List.of();
        }
    }

    public void requireApprovedSellers(List<UUID> ids) {
        try {
            List<SellerStatus> statuses =
                    builder.baseUrl(p.sellerBaseUrl())
                            .build()
                            .post()
                            .uri("/internal/v1/sellers/status-validations")
                            .header("X-Internal-Service-Key", p.internalServiceKey())
                            .body(Map.of("sellerIds", ids))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<SellerStatus>>() {});
            if (statuses == null
                    || statuses.size() != new HashSet<>(ids).size()
                    || statuses.stream().anyMatch(s -> !"APPROVED".equals(s.status())))
                conflict("SELLER_INACTIVE_409", "A seller is not approved for checkout.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            unavailable("Seller validation is unavailable.");
        }
    }

    public List<Availability> availability(List<CartLine> lines) {
        try {
            List<RawAvailability> response =
                    builder.baseUrl(p.inventoryBaseUrl())
                            .build()
                            .post()
                            .uri("/internal/v1/inventory/availability")
                            .header("X-Internal-Service-Key", p.internalServiceKey())
                            .body(
                                    Map.of(
                                            "variantIds",
                                            lines.stream().map(CartLine::variantId).toList()))
                            .retrieve()
                            .body(new ParameterizedTypeReference<List<RawAvailability>>() {});
            if (response == null) unavailable("Inventory validation returned no response.");
            return response.stream()
                    .map(
                            value ->
                                    new Availability(
                                            value.variantId(), value.onHand() - value.reserved()))
                    .toList();
        } catch (RuntimeException e) {
            unavailable("Inventory validation is unavailable.");
            return List.of();
        }
    }

    private static void forbidden(String m) {
        throw new ApiException(HttpStatus.FORBIDDEN, "CUSTOMER_INACTIVE_403", m);
    }

    private static void conflict(String c, String m) {
        throw new ApiException(HttpStatus.CONFLICT, c, m);
    }

    private static void unavailable(String m) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE_503", m);
    }

    private record UserSummary(UUID userId, boolean active, List<String> roles) {}

    private record CartRequest(UUID customerId, UUID cartId, long cartVersion) {}

    private record SellerStatus(UUID sellerId, String status) {}

    private record RawAvailability(UUID variantId, int onHand, int reserved) {}

    private record RawCatalogLine(
            UUID variantId,
            UUID productId,
            UUID sellerId,
            String productName,
            String variantName,
            String sku,
            String priceAmount,
            String priceCurrency,
            String status,
            Long productVersion,
            Long variantVersion) {}
}
