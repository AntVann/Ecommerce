package com.marketflow.payment.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "marketflow.outbox.enabled", matchIfMissing = true)
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    public OutboxPublisher(
            JdbcTemplate jdbc, KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${marketflow.outbox.poll-delay:1000}")
    void publish() {
        Long pending =
                jdbc.queryForObject(
                        "SELECT count(*) FROM outbox_event WHERE published_at IS NULL", Long.class);
        meters.gauge("payment.outbox.unpublished", pending == null ? 0 : pending);
        jdbc.query(
                        "SELECT event_id,event_type,aggregate_id,payload::text FROM outbox_event WHERE published_at IS NULL AND next_attempt_at<=now() ORDER BY occurred_at LIMIT 50",
                        (r, n) ->
                                new Event(
                                        r.getObject(1, UUID.class),
                                        r.getString(2),
                                        r.getObject(3, UUID.class),
                                        r.getString(4)))
                .forEach(this::send);
    }

    private void send(Event event) {
        kafka.send("marketflow.payment.events.v1", event.aggregateId().toString(), event.payload())
                .whenComplete(
                        (result, failure) -> {
                            if (failure == null) {
                                jdbc.update(
                                        "UPDATE outbox_event SET published_at=now() WHERE event_id=? AND published_at IS NULL",
                                        event.id());
                            } else {
                                jdbc.update(
                                        "UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=now()+interval '30 seconds' WHERE event_id=?",
                                        event.id());
                                LoggerFactory.getLogger(getClass())
                                        .atWarn()
                                        .addKeyValue("event.id", event.id())
                                        .addKeyValue("event.type", event.type())
                                        .log("Payment outbox publication deferred");
                            }
                        });
    }

    private record Event(UUID id, String type, UUID aggregateId, String payload) {}
}
