package com.marketflow.notification.infrastructure.messaging;

import com.marketflow.notification.application.NotificationService;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class NotificationRabbitConsumer {
    private final NotificationService service;
    private final ObjectMapper mapper;

    public NotificationRabbitConsumer(NotificationService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @RabbitListener(queues = RabbitConfiguration.QUEUE)
    public void consume(String payload) {
        try {
            String value = payload;
            if (payload.trim().startsWith("{")) {
                value = mapper.readTree(payload).path("jobId").asText("");
            }
            if (!value.isBlank()) service.deliver(UUID.fromString(value.trim()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid notification delivery command", exception);
        }
    }
}
