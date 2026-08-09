package com.marketflow.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.marketflow.notification.application.NotificationModels.CreateCommand;
import com.marketflow.notification.application.NotificationModels.NotificationView;
import com.marketflow.notification.application.NotificationService;
import com.marketflow.notification.application.NotificationStore;
import com.marketflow.notification.domain.NotificationKind;
import com.marketflow.notification.domain.NotificationStatus;
import com.marketflow.notification.infrastructure.provider.FakeEmailProperties;
import com.marketflow.notification.infrastructure.provider.FakeEmailProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class NotificationServiceTest {
    @Test
    void transientFailureIsRetriedAndSuccessDelivered() {
        NotificationStore store = mock(NotificationStore.class);
        UUID id = UUID.randomUUID();
        when(store.createOrGet(any(), any())).thenReturn(id);
        when(store.claimDue(eq(id), any()))
                .thenReturn(Optional.of(view(id, 0, NotificationStatus.PROCESSING)))
                .thenReturn(Optional.of(view(id, 1, NotificationStatus.PROCESSING)));
        when(store.recordAttempt(eq(id), anyInt(), any())).thenReturn(UUID.randomUUID());
        FakeEmailProvider provider =
                new FakeEmailProvider(new FakeEmailProperties("transient-failure", 1));
        NotificationService service =
                new NotificationService(
                        store,
                        provider,
                        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                        new SimpleMeterRegistry(),
                        new MockEnvironment());
        service.enqueue(
                new CreateCommand(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        NotificationKind.ORDER_CONFIRMATION,
                        "a@example.invalid",
                        "order-confirmation",
                        1,
                        "{}"));
        service.deliver(id);
        service.deliver(id);
        verify(store).retry(eq(id), any(), eq("PROVIDER_UNAVAILABLE"), any(), any());
        verify(store).delivered(eq(id), any(), startsWith("fake-email-"), any());
    }

    private NotificationView view(UUID id, int attempts, NotificationStatus status) {
        return new NotificationView(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                NotificationKind.ORDER_CONFIRMATION,
                "a@example.invalid",
                "order-confirmation",
                1,
                status,
                attempts,
                null,
                Instant.EPOCH,
                Instant.EPOCH);
    }
}
