package com.marketflow.catalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.marketflow.catalog.api.ApiException;
import com.marketflow.catalog.infrastructure.security.CatalogSecurityProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class SellerAuthorizerTest {
    @Test
    void failsClosedWhenSellerAuthorizationIsUnavailable() {
        var properties =
                new CatalogSecurityProperties(
                        "http://identity.invalid",
                        "issuer",
                        "audience",
                        "http://127.0.0.1:1",
                        "test-key");
        var authorizer =
                new SellerAuthorizer(
                        RestClient.builder().baseUrl(properties.sellerBaseUrl()).build(),
                        properties);
        assertThatThrownBy(
                        () ->
                                authorizer.require(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "CATALOG_WRITE",
                                        "failure-test"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("SELLER_DEPENDENCY_UNAVAILABLE_503");
    }
}
