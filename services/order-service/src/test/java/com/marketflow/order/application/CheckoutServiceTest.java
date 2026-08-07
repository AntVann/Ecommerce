package com.marketflow.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.CheckoutModels.Availability;
import com.marketflow.order.application.CheckoutModels.CartLine;
import com.marketflow.order.application.CheckoutModels.CartSnapshot;
import com.marketflow.order.application.CheckoutModels.CatalogLine;
import com.marketflow.order.application.CheckoutModels.CheckoutCommand;
import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.domain.Address;
import com.marketflow.order.infrastructure.security.OrderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CheckoutServiceTest {
    private OrderRepository repository;
    private CheckoutGateways gateways;
    private CheckoutService service;
    private final UUID customer = UUID.randomUUID(), cart = UUID.randomUUID();
    private final Address address =
            new Address("Ada Buyer", "1 Market St", null, "Oakland", "CA", "94601", "US");

    @BeforeEach
    void setup() {
        repository = mock(OrderRepository.class);
        gateways = mock(CheckoutGateways.class);
        OrderProperties p =
                new OrderProperties("i", "iss", "aud", "c", "cat", "s", "inv", "key", 900);
        service =
                new CheckoutService(
                        repository,
                        gateways,
                        new ObjectMapper(),
                        p,
                        new SimpleMeterRegistry(),
                        Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void identicalIdempotentRetryReturnsOriginalWithoutDependencies() {
        CheckoutCommand command = new CheckoutCommand(cart, 1, address, address);
        OrderView order = view();
        when(repository.idempotency(eq(customer), any(), eq("abcdefghijklmnop")))
                .thenReturn(
                        Optional.of(
                                new OrderRepository.Idempotency(hash(command), order.id(), 202)));
        when(repository.owned(order.id(), customer)).thenReturn(Optional.of(order));
        assertThat(service.checkout(customer, "abcdefghijklmnop", command, "c")).isEqualTo(order);
        verifyNoInteractions(gateways);
    }

    @Test
    void reusedKeyWithDifferentInputIsRejected() {
        CheckoutCommand command = new CheckoutCommand(cart, 1, address, address);
        when(repository.idempotency(eq(customer), any(), any()))
                .thenReturn(
                        Optional.of(
                                new OrderRepository.Idempotency(
                                        "different", UUID.randomUUID(), 202)));
        assertThatThrownBy(() -> service.checkout(customer, "abcdefghijklmnop", command, "c"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different input");
        verifyNoInteractions(gateways);
    }

    @Test
    void checkoutRevalidatesAndCreatesOneOutboxRecord() {
        UUID product = UUID.randomUUID(), variant = UUID.randomUUID(), seller = UUID.randomUUID();
        CheckoutCommand command = new CheckoutCommand(cart, 2, address, address);
        CartLine cartLine = new CartLine(product, variant, 2);
        CatalogLine fact =
                new CatalogLine(
                        product,
                        variant,
                        seller,
                        "Coffee",
                        "Large",
                        "CF-L",
                        new BigDecimal("12.3400"),
                        "USD",
                        5,
                        true,
                        null);
        when(repository.idempotency(any(), any(), any())).thenReturn(Optional.empty());
        when(gateways.cart(customer, cart, 2))
                .thenReturn(new CartSnapshot(cart, 2, List.of(cartLine)));
        when(gateways.catalog(any())).thenReturn(List.of(fact));
        when(gateways.availability(any())).thenReturn(List.of(new Availability(variant, 2)));
        when(repository.claim(any(), any(), any(), any(), any())).thenReturn(true);
        when(repository.cartOrder(any(), any(), anyLong())).thenReturn(Optional.empty());
        when(repository.order(any()))
                .thenAnswer(
                        i ->
                                Optional.of(
                                        new OrderView(
                                                i.getArgument(0),
                                                customer,
                                                cart,
                                                2,
                                                "PENDING",
                                                null,
                                                "USD",
                                                new BigDecimal("24.6800"),
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                new BigDecimal("24.6800"),
                                                address,
                                                address,
                                                1,
                                                Instant.now(),
                                                Instant.now(),
                                                List.of())));
        OrderView result = service.checkout(customer, "abcdefghijklmnop", command, "c");
        assertThat(result.status()).isEqualTo("PENDING");
        verify(gateways).requireActiveCustomer(customer);
        verify(gateways).requireApprovedSellers(List.of(seller));
        verify(repository, times(1)).orderCreatedOutbox(any(), eq(900L), eq("c"), any());
        verify(repository, times(1))
                .complete(eq(customer), any(), eq("abcdefghijklmnop"), any(), eq(202), any());
    }

    private OrderView view() {
        return new OrderView(
                UUID.randomUUID(),
                customer,
                cart,
                1,
                "PENDING",
                null,
                "USD",
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                address,
                address,
                1,
                Instant.now(),
                Instant.now(),
                List.of());
    }

    private String hash(CheckoutCommand command) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(new ObjectMapper().writeValueAsBytes(command)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
