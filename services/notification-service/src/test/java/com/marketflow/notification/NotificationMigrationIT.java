package com.marketflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        properties = {
            "spring.kafka.listener.auto-startup=false",
            "spring.rabbitmq.listener.simple.auto-startup=false",
            "marketflow.notification.outbox.enabled=false"
        })
class NotificationMigrationIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationCreatesReliabilityTables() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('notification_job','notification_attempt','notification_inbox','notification_outbox','notification_template')",
                                Integer.class))
                .isEqualTo(5);
    }

    @Test
    void inboxClaimIsIdempotent() throws Exception {
        UUID event = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(() -> claim(start, event));
            Future<Integer> second = pool.submit(() -> claim(start, event));
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
    }

    private int claim(CountDownLatch start, UUID event) throws Exception {
        start.await();
        return jdbc.update(
                "INSERT INTO notification_inbox(consumer,event_id,processed_at) VALUES ('test',?,now()) ON CONFLICT DO NOTHING",
                event);
    }
}
