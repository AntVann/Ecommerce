package com.marketflow.catalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.catalog.api.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SELLER = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    @Mock CatalogRepository repository;
    @Mock SellerAuthorizer authorizer;

    @Test
    void publicationRequiresAnActiveVariantAndReadyImage() {
        var product =
                new CatalogRepository.Product(
                        PRODUCT,
                        SELLER,
                        UUID.randomUUID(),
                        "Product",
                        "Description",
                        "DRAFT",
                        "{}",
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null);
        when(repository.find(PRODUCT)).thenReturn(Optional.of(product));
        when(repository.variants(PRODUCT)).thenReturn(List.of());
        when(repository.images(PRODUCT)).thenReturn(List.of());
        var service =
                new CatalogService(
                        repository,
                        authorizer,
                        new SimpleMeterRegistry(),
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.publish(USER, SELLER, PRODUCT, 1, "test"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("CATALOG_NOT_PUBLISHABLE_422");
        verify(authorizer).require(SELLER, USER, "CATALOG_WRITE", "test");
    }
}
