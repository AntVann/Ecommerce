package com.marketflow.cart.infrastructure;

import com.marketflow.cart.application.CatalogGateway;
import com.marketflow.cart.application.IdentityGateway;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Configuration
class ServiceClients {
    @Bean("catalogRestClient")
    RestClient catalogRestClient(CartProperties properties) {
        return client(properties.catalogBaseUrl());
    }

    @Bean("identityRestClient")
    RestClient identityRestClient(CartProperties properties) {
        return client(properties.identityBaseUrl());
    }

    private RestClient client(String baseUrl) {
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(2));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
}

@Component
class HttpCatalogGateway implements CatalogGateway {
    private final RestClient client;
    private final CartProperties properties;

    HttpCatalogGateway(
            @Qualifier("catalogRestClient") RestClient catalogRestClient,
            CartProperties properties) {
        this.client = catalogRestClient;
        this.properties = properties;
    }

    @Override
    public VariantQuote quote(UUID variantId) {
        CatalogValidation[] response =
                client.post()
                        .uri("/internal/v1/catalog/checkout-validations")
                        .header("X-Internal-Service-Key", properties.internalServiceKey())
                        .body(new ValidationRequest(List.of(variantId)))
                        .retrieve()
                        .body(CatalogValidation[].class);
        if (response == null || response.length == 0) {
            return new VariantQuote(null, variantId, null, false, null, null, 0, "NOT_FOUND");
        }
        CatalogValidation value = response[0];
        return new VariantQuote(
                value.productId(),
                value.variantId(),
                value.sellerId(),
                "VALID".equals(value.status()),
                value.priceAmount() == null ? null : new BigDecimal(value.priceAmount()),
                value.priceCurrency(),
                value.productVersion() == null ? 0 : value.productVersion(),
                value.status());
    }

    record ValidationRequest(List<UUID> variantIds) {}

    record CatalogValidation(
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

@Component
class HttpIdentityGateway implements IdentityGateway {
    private final RestClient client;
    private final CartProperties properties;

    HttpIdentityGateway(
            @Qualifier("identityRestClient") RestClient identityRestClient,
            CartProperties properties) {
        this.client = identityRestClient;
        this.properties = properties;
    }

    @Override
    public boolean activeCustomer(UUID userId) {
        UserSummary summary =
                client.get()
                        .uri("/internal/v1/users/{id}", userId)
                        .header("X-Internal-Service-Key", properties.internalServiceKey())
                        .retrieve()
                        .body(UserSummary.class);
        return summary != null && summary.active() && summary.roles().contains("CUSTOMER");
    }

    record UserSummary(UUID userId, boolean active, List<String> roles) {}
}
