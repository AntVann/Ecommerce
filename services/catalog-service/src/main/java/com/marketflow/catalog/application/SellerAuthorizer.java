package com.marketflow.catalog.application;

import com.marketflow.catalog.api.ApiException;
import com.marketflow.catalog.infrastructure.security.CatalogSecurityProperties;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public final class SellerAuthorizer {
    private final RestClient seller;
    private final CatalogSecurityProperties properties;

    public SellerAuthorizer(RestClient sellerRestClient, CatalogSecurityProperties properties) {
        this.seller = sellerRestClient;
        this.properties = properties;
    }

    public void require(UUID sellerId, UUID userId, String permission, String correlationId) {
        try {
            Authorization response =
                    seller.get()
                            .uri(
                                    uri ->
                                            uri.path(
                                                            "/internal/v1/sellers/{sellerId}/authorization")
                                                    .queryParam("userId", userId)
                                                    .queryParam("permission", permission)
                                                    .build(sellerId))
                            .header("X-Internal-Service-Key", properties.internalServiceKey())
                            .header("X-Correlation-ID", correlationId)
                            .retrieve()
                            .body(Authorization.class);
            if (response == null || !response.authorized()) {
                throw new ApiException(
                        HttpStatus.NOT_FOUND,
                        "CATALOG_RESOURCE_NOT_FOUND_404",
                        "Catalog resource was not found.");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
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
