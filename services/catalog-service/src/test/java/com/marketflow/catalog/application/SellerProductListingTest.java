package com.marketflow.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerProductListingTest {
    @Mock CatalogRepository repository;
    @Mock SellerAuthorizer authorizer;

    @Test
    void sellerListingAuthorizesBeforeReturningOwnedProducts() {
        UUID user = UUID.randomUUID();
        UUID seller = UUID.randomUUID();
        var product =
                new CatalogRepository.Product(
                        UUID.randomUUID(),
                        seller,
                        UUID.randomUUID(),
                        "Demo",
                        "Description",
                        "DRAFT",
                        "{}",
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        null);
        when(repository.sellerProducts(seller, "DRAFT", 100)).thenReturn(List.of(product));
        when(repository.variants(product.id())).thenReturn(List.of());
        when(repository.images(product.id())).thenReturn(List.of());
        when(repository.map("{}")).thenReturn(Map.of());
        var service = new CatalogService(repository, authorizer, new SimpleMeterRegistry());

        assertThat(service.sellerProducts(user, seller, "DRAFT", 100, "corr"))
                .singleElement()
                .extracting(view -> view.product().id())
                .isEqualTo(product.id());
        verify(authorizer).require(seller, user, "CATALOG_WRITE", "corr");
    }
}
