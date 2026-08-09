package com.marketflow.notification.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "marketflow.notification.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final MeterRegistry meters;

    public NotificationOutboxPublisher(
            JdbcTemplate jdbc, RabbitTemplate rabbit, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${marketflow.notification.outbox.poll-delay:1000}")
    void publish() {
        jdbc.query(
                        "SELECT o.id,o.job_id,o.routing_key,o.payload::text FROM notification_outbox o JOIN notification_job j ON j.id=o.job_id WHERE o.published_at IS NULL AND j.next_attempt_at<=now() ORDER BY o.created_at LIMIT 50",
                        (r, n) ->
                                new Entry(
                                        r.getObject(1, UUID.class),
                                        r.getObject(2, UUID.class),
                                        r.getString(3),
                                        r.getString(4)))
                .forEach(this::send);
    }

    private void send(Entry e) {
        rabbit.convertAndSend(RabbitConfiguration.EXCHANGE, e.routing(), e.job().toString());
        jdbc.update(
                "UPDATE notification_outbox SET published_at=now() WHERE id=? AND published_at IS NULL",
                e.id());
        meters.counter("notification.outbox.published").increment();
    }

    private record Entry(UUID id, UUID job, String routing, String payload) {}
}
