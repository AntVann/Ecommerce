package com.marketflow.cart.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.marketflow.cart.api.ApiException;
import com.marketflow.cart.application.CartService.Actor;
import com.marketflow.cart.domain.Cart.ActorType;
import com.marketflow.cart.infrastructure.CartProperties;
import com.marketflow.cart.infrastructure.RedisCartRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartServiceTest {
    private RedisCartRepository repository;
    private CatalogGateway catalog;
    private IdentityGateway identity;
    private CartService service;
    private final Actor guest = new Actor(ActorType.GUEST, "digest");

    @BeforeEach
    void setup() {
        repository = mock(RedisCartRepository.class);
        catalog = mock(CatalogGateway.class);
        identity = mock(IdentityGateway.class);
        CartProperties properties =
                new CartProperties(
                        "http://identity",
                        "issuer",
                        "audience",
                        "http://catalog",
                        "key",
                        "test:cart:v1",
                        Duration.ofDays(7),
                        Duration.ofDays(30),
                        100,
                        false);
        service =
                new CartService(
                        repository, catalog, identity, properties, new SimpleMeterRegistry());
    }

    @Test
    void rejectsQuantityOutsideBounds() {
        assertThatThrownBy(() -> service.add(guest, UUID.randomUUID(), 100))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("between 1 and 99");
    }

    @Test
    void storesCurrentQuoteAsAdvisoryEstimate() {
        UUID variant = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        when(catalog.quote(variant))
                .thenReturn(
                        new CatalogGateway.VariantQuote(
                                product,
                                variant,
                                UUID.randomUUID(),
                                true,
                                new BigDecimal("12.3400"),
                                "USD",
                                4,
                                null));
        when(repository.find(anyString())).thenReturn(Optional.empty());
        when(repository.create(anyString(), any(), any())).thenReturn(true);
        when(repository.replace(anyString(), anyLong(), any(), any())).thenReturn(true);

        var cart = service.add(guest, variant, 2);

        assertThat(cart.items().get(variant).estimatedUnitPrice()).isEqualByComparingTo("12.3400");
        assertThat(cart.items().get(variant).quantity()).isEqualTo(2);
    }

    @Test
    void mergeRequiresLiveCustomer() {
        UUID customer = UUID.randomUUID();
        when(identity.activeCustomer(customer)).thenReturn(false);
        assertThatThrownBy(() -> service.merge("guest", customer))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }
}
