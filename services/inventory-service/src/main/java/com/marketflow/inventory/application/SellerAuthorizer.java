package com.marketflow.inventory.application;

import com.marketflow.inventory.api.ApiException;
import com.marketflow.inventory.infrastructure.security.InventorySecurityProperties;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public final class SellerAuthorizer {
    private final RestClient seller;
    private final InventorySecurityProperties properties;

    public SellerAuthorizer(RestClient sellerRestClient, InventorySecurityProperties properties) {
        seller = sellerRestClient;
        this.properties = properties;
    }

    public void require(UUID sellerId, UUID userId, String correlationId) {
        try {
            Authorization a =
                    seller.get()
                            .uri(
                                    u ->
                                            u.path("/internal/v1/sellers/{sellerId}/authorization")
                                                    .queryParam("userId", userId)
                                                    .queryParam("permission", "INVENTORY_WRITE")
                                                    .build(sellerId))
                            .header("X-Internal-Service-Key", properties.internalServiceKey())
                            .header("X-Correlation-ID", correlationId)
                            .retrieve()
                            .body(Authorization.class);
            if (a == null || !a.authorized())
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "INVENTORY_RESOURCE_NOT_FOUND_404",
                        "Inventory resource was not found.");
        } catch (ApiException e) {
            throw e;
        } catch (RestClientException e) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SELLER_DEPENDENCY_UNAVAILABLE_503",
                    "Seller authorization is temporarily unavailable.");
        }
    }

    record Authorization(
            UUID sellerId,
            UUID userId,
            String sellerStatus,
            String permission,
            boolean authorized) {}
}
