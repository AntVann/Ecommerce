package com.marketflow.notification.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfiguration {
    public static final String EXCHANGE = "marketflow.notification.commands.v1";
    public static final String QUEUE = "marketflow.notification.email.v1";
    public static final String DLX = "marketflow.notification.dlx.v1";
    public static final String DLQ = "marketflow.notification.email.dlq.v1";
    public static final String RETRY_1 = "notification.email.retry.1.v1";
    public static final String RETRY_2 = "notification.email.retry.2.v1";
    public static final String RETRY_3 = "notification.email.retry.3.v1";
    private static final String MAIN_ROUTE = "notification.email.order-confirmation.v1";

    @Bean
    DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    Queue emailQueue() {
        return new Queue(
                QUEUE,
                true,
                false,
                false,
                java.util.Map.of("x-dead-letter-exchange", DLX, "x-dead-letter-routing-key", DLQ));
    }

    @Bean
    Queue deadLetterQueue() {
        return new Queue(DLQ, true);
    }

    @Bean
    Queue retryQueue1() {
        return retryQueue(RETRY_1, 10_000);
    }

    @Bean
    Queue retryQueue2() {
        return retryQueue(RETRY_2, 60_000);
    }

    @Bean
    Queue retryQueue3() {
        return retryQueue(RETRY_3, 300_000);
    }

    private Queue retryQueue(String name, int ttl) {
        return new Queue(
                name,
                true,
                false,
                false,
                java.util.Map.of(
                        "x-message-ttl", ttl,
                        "x-dead-letter-exchange", EXCHANGE,
                        "x-dead-letter-routing-key", MAIN_ROUTE));
    }

    @Bean
    Binding emailBinding(Queue emailQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(notificationExchange)
                .with("notification.email.order-confirmation.v1");
    }

    @Bean
    Binding shipmentBinding(Queue emailQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(emailQueue)
                .to(notificationExchange)
                .with("notification.email.shipment.v1");
    }

    @Bean
    Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(DLQ);
    }

    @Bean
    Binding retryBinding1(Queue retryQueue1, DirectExchange notificationExchange) {
        return BindingBuilder.bind(retryQueue1).to(notificationExchange).with(RETRY_1);
    }

    @Bean
    Binding retryBinding2(Queue retryQueue2, DirectExchange notificationExchange) {
        return BindingBuilder.bind(retryQueue2).to(notificationExchange).with(RETRY_2);
    }

    @Bean
    Binding retryBinding3(Queue retryQueue3, DirectExchange notificationExchange) {
        return BindingBuilder.bind(retryQueue3).to(notificationExchange).with(RETRY_3);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template = new RabbitTemplate(factory);
        template.setMessageConverter(new SimpleMessageConverter());
        return template;
    }
}
