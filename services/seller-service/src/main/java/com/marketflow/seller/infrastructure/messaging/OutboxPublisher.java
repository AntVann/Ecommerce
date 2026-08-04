package com.marketflow.seller.infrastructure.messaging;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marketflow.outbox.enabled", matchIfMissing = true)
public final class OutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${marketflow.outbox.poll-delay:1000}")
    public void publish() {
        jdbc.query(
                        """
                        SELECT event_id, event_type, aggregate_id, payload::text
                        FROM outbox_event
                        WHERE published_at IS NULL AND next_attempt_at <= now()
                        ORDER BY occurred_at LIMIT 50
                        """,
                        (rs, row) ->
                                new PendingEvent(
                                        rs.getObject("event_id", UUID.class),
                                        rs.getString("event_type"),
                                        rs.getObject("aggregate_id", UUID.class),
                                        rs.getString("payload")))
                .forEach(this::send);
    }

    private void send(PendingEvent event) {
        kafka.send("marketflow.seller.events.v1", event.aggregateId().toString(), event.payload())
                .whenComplete(
                        (result, failure) -> {
                            if (failure == null) {
                                jdbc.update(
                                        "UPDATE outbox_event SET published_at = now() WHERE event_id = ?",
                                        event.id());
                            } else {
                                jdbc.update(
                                        """
                                UPDATE outbox_event SET attempts = attempts + 1,
                                    next_attempt_at = now() + interval '30 seconds'
                                WHERE event_id = ?
                                """,
                                        event.id());
                                LOGGER.atWarn()
                                        .addKeyValue("event.id", event.id())
                                        .addKeyValue("event.type", event.type())
                                        .log("Seller outbox publication deferred");
                            }
                        });
    }

    private record PendingEvent(UUID id, String type, UUID aggregateId, String payload) {}
}
