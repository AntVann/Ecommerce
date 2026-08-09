package com.marketflow.inventory.application;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    @Test
    void duplicateConfirmationEventDoesNotCommitStock() {
        UUID eventId = UUID.randomUUID();
        when(repository.processed("inventory-order-v1", eventId)).thenReturn(true);

        service().confirmOrderReservation(eventId, UUID.randomUUID(), "test");

        verify(repository, never()).reservationForUpdate(org.mockito.ArgumentMatchers.any());
        verify(repository, never())
                .commit(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void confirmationCommitsLinesAndPublishesOneReservationEvent() {
        UUID eventId = UUID.randomUUID();
        UUID referenceId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Instant now = Instant.EPOCH;
        var reservation =
                new InventoryRepository.Reservation(
                        reservationId, referenceId, "PENDING", now, now, now);
        when(repository.reservationForUpdate(referenceId))
                .thenReturn(java.util.Optional.of(reservation));
        when(repository.reservationLines(reservationId))
                .thenReturn(
                        List.of(
                                new InventoryRepository.ReservationLine(
                                        reservationId, variantId, 2)));
        when(repository.item(variantId))
                .thenReturn(
                        java.util.Optional.of(
                                new InventoryRepository.Item(variantId, sellerId, 5, 2, 3, now)));
        when(repository.completeReservation(reservationId, "PENDING", "CONFIRMED", now))
                .thenReturn(true);
        when(repository.reservation(referenceId))
                .thenReturn(
                        java.util.Optional.of(
                                new InventoryRepository.Reservation(
                                        reservationId, referenceId, "CONFIRMED", now, now, now)));

        service().confirmOrderReservation(eventId, referenceId, "test");

        verify(repository).commit(variantId, 2, now);
        verify(repository, times(1))
                .reservationOutbox(
                        "inventory.inventory-reservation-confirmed.v1",
                        reservationId,
                        1,
                        "test",
                        Map.of(
                                "referenceId", referenceId,
                                "reservationId", reservationId,
                                "status", "CONFIRMED"),
                        now);
    }

    @Test
    void alreadyReleasedReservationMakesDuplicateBusinessCommandHarmless() {
        UUID referenceId = UUID.randomUUID();
        Instant now = Instant.EPOCH;
        when(repository.reservationForUpdate(referenceId))
                .thenReturn(
                        java.util.Optional.of(
                                new InventoryRepository.Reservation(
                                        UUID.randomUUID(),
                                        referenceId,
                                        "RELEASED",
                                        now,
                                        now,
                                        now)));

        service()
                .releaseOrderReservation(
                        UUID.randomUUID(), referenceId, "PAYMENT_DECLINED", "test");

        verify(repository, never())
                .release(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.any());
        verify(repository, never())
                .reservationOutbox(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyMap(),
                        org.mockito.ArgumentMatchers.any());
    }

    private InventoryService service() {
        return new InventoryService(
                repository,
                authorizer,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }
}
