package com.marketflow.payment;

import static org.assertj.core.api.Assertions.assertThat;

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
            "marketflow.outbox.enabled=false",
            "spring.kafka.listener.auto-startup=false"
        })
class PaymentMigrationIT {
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
    void migrationCreatesPaymentReliabilityTables() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('payment','payment_attempt','provider_callback','idempotency_record','outbox_event','processed_message')",
                                Integer.class))
                .isEqualTo(6);
    }

    @Test
    void concurrentIdempotencyClaimHasOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = pool.submit(() -> claim(start));
            Future<Integer> second = pool.submit(() -> claim(start));
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
    }

    private int claim(CountDownLatch start) throws Exception {
        start.await();
        return jdbc.update(
                "INSERT INTO idempotency_record(operation,idempotency_key,request_hash,created_at,expires_at) VALUES ('AUTHORIZE_PAYMENT','payment-key-123',repeat('a',64),now(),now()+interval '1 day') ON CONFLICT DO NOTHING");
    }
}
