package com.marketflow.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CheckoutValidationTest {
    @Mock CatalogRepository repository;
    @Mock SellerAuthorizer authorizer;

    @Test
    void returnsAuthoritativePriceAndExplicitStatusForEveryRequestedVariant() {
        UUID valid = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(repository.checkoutVariants(List.of(valid, missing)))
                .thenReturn(
                        List.of(
                                new CatalogRepository.CheckoutVariant(
                                        valid,
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        "Product",
                                        "Variant",
                                        "SKU",
                                        new BigDecimal("12.3400"),
                                        "USD",
                                        true,
                                        "ACTIVE",
                                        "APPROVED",
                                        4,
                                        2)));
        var service =
                new CatalogService(
                        repository,
                        authorizer,
                        new SimpleMeterRegistry(),
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var results = service.validateCheckout(List.of(valid, missing, valid));

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().status()).isEqualTo("VALID");
        assertThat(results.getFirst().priceAmount()).isEqualTo("12.3400");
        assertThat(results.getLast().status()).isEqualTo("NOT_FOUND");
    }
}
