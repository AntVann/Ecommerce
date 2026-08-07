package com.marketflow.inventory.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderReservationIdempotencyTest {
    @Mock InventoryRepository repository;
    @Mock SellerAuthorizer authorizer;

    @Test
    void duplicateOrderEventDoesNotReserveAgain() {
        UUID eventId = UUID.randomUUID();
        when(repository.processed("inventory-order-v1", eventId)).thenReturn(true);
        var service = service();

        service.reserveOrderEvent(
                eventId,
                UUID.randomUUID(),
                List.of(new InventoryService.ReserveLine(UUID.randomUUID(), 1)),
                Duration.ofMinutes(15),
                "test");

        verify(repository, never())
                .createReservation(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void duplicateFailedEventDoesNotPublishAnotherFailure() {
        UUID eventId = UUID.randomUUID();
        when(repository.processed("inventory-order-v1", eventId)).thenReturn(true);
        var service = service();

        service.recordOrderReservationFailure(
                eventId, UUID.randomUUID(), "INSUFFICIENT_AVAILABLE", "test");

        verify(repository, never())
                .outbox(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(), org.mockito.ArgumentMatchers.any());
    }

    private InventoryService service() {
        return new InventoryService(
                repository,
                authorizer,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
}
