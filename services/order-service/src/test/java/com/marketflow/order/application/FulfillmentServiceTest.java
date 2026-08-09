package com.marketflow.order.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.FulfillmentModels.ShipmentLineRequest;
import com.marketflow.order.application.FulfillmentModels.ShipmentView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FulfillmentServiceTest {
    private OrderRepository repository;
    private OrderSagaGateways gateways;
    private FulfillmentService service;
    private UUID user;
    private UUID seller;
    private UUID order;

    @BeforeEach
    void setup() {
        repository = org.mockito.Mockito.mock(OrderRepository.class);
        gateways = org.mockito.Mockito.mock(OrderSagaGateways.class);
        service = new FulfillmentService(repository, gateways);
        user = UUID.randomUUID();
        seller = UUID.randomUUID();
        order = UUID.randomUUID();
    }

    @Test
    void rejectsDuplicateOrderLinesBeforePersistence() {
        UUID line = UUID.randomUUID();
        assertThatThrownBy(
                        () ->
                                service.create(
                                        user,
                                        seller,
                                        order,
                                        "shipment-key-123456",
                                        "Acme",
                                        "TRK123",
                                        List.of(
                                                new ShipmentLineRequest(line, 1),
                                                new ShipmentLineRequest(line, 1)),
                                        "corr"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("only once");
    }

    @Test
    void rejectsIdempotencyReuseWithDifferentRequest() {
        when(repository.idempotencyHash(seller, "shipment-key-123456"))
                .thenReturn(Optional.of("different"));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        user,
                                        seller,
                                        order,
                                        "shipment-key-123456",
                                        "Acme",
                                        "TRK123",
                                        List.of(new ShipmentLineRequest(UUID.randomUUID(), 1)),
                                        "corr"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("different shipment data");
        verify(gateways).requireFulfillmentPermission(seller, user);
    }

    @Test
    void rejectsInvalidTransition() {
        UUID shipment = UUID.randomUUID();
        ShipmentView view =
                new ShipmentView(
                        shipment,
                        order,
                        seller,
                        "CREATED",
                        "Acme",
                        "TRK123",
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of());
        when(repository.shipment(shipment)).thenReturn(Optional.of(view));
        assertThatThrownBy(() -> service.transition(user, seller, shipment, 1, "DELIVERED", "corr"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("transition is invalid");
    }

    @Test
    void rejectsStaleShipmentVersion() {
        UUID shipment = UUID.randomUUID();
        ShipmentView view =
                new ShipmentView(
                        shipment,
                        order,
                        seller,
                        "CREATED",
                        "Acme",
                        "TRK123",
                        4,
                        Instant.EPOCH,
                        Instant.EPOCH,
                        List.of());
        when(repository.shipment(shipment)).thenReturn(Optional.of(view));
        assertThatThrownBy(
                        () -> service.transition(user, seller, shipment, 3, "IN_TRANSIT", "corr"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("changed by another request");
    }
}
