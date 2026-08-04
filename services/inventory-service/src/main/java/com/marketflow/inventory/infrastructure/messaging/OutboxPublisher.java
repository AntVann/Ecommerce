package com.marketflow.inventory.infrastructure.messaging;

import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marketflow.outbox.enabled", matchIfMissing = true)
public final class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(JdbcTemplate jdbc, KafkaTemplate<String, String> kafka) {
        this.jdbc = jdbc;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${marketflow.outbox.poll-delay:1000}")
    void publish() {
        jdbc.query(
                        "SELECT event_id,event_type,aggregate_id,payload::text FROM outbox_event WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY occurred_at LIMIT 50",
                        (rs, row) ->
                                new Event(
                                        rs.getObject("event_id", UUID.class),
                                        rs.getString("event_type"),
                                        rs.getObject("aggregate_id", UUID.class),
                                        rs.getString("payload")))
                .forEach(this::send);
    }

    private void send(Event e) {
        kafka.send("marketflow.inventory.events.v1", e.aggregateId().toString(), e.payload())
                .whenComplete(
                        (result, failure) -> {
                            if (failure == null)
                                jdbc.update(
                                        "UPDATE outbox_event SET published_at=now() WHERE event_id=? AND published_at IS NULL",
                                        e.id());
                            else {
                                jdbc.update(
                                        "UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=now()+interval '30 seconds' WHERE event_id=?",
                                        e.id());
                                LoggerFactory.getLogger(OutboxPublisher.class)
                                        .atWarn()
                                        .addKeyValue("event.id", e.id())
                                        .addKeyValue("event.type", e.type())
                                        .log("Inventory outbox publication deferred");
                            }
                        });
    }

    private record Event(UUID id, String type, UUID aggregateId, String payload) {}
}
