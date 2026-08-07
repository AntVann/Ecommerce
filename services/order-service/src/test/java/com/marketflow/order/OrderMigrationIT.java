package com.marketflow.order;

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
            "marketflow.outbox.enabled=false",
            "spring.kafka.listener.auto-startup=false"
        })
class OrderMigrationIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationCreatesOwnedTables() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('customer_order','order_item','order_saga','idempotency_record','outbox_event','processed_message')",
                                Integer.class))
                .isEqualTo(6);
    }

    @Test
    void concurrentIdempotencyClaimHasOneWinner() throws Exception {
        UUID customer = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<Integer> a = pool.submit(() -> claim(start, customer));
            Future<Integer> b = pool.submit(() -> claim(start, customer));
            start.countDown();
            assertThat(a.get() + b.get()).isEqualTo(1);
        }
    }

    private int claim(CountDownLatch start, UUID customer) throws Exception {
        start.await();
        return jdbc.update(
                "INSERT INTO idempotency_record(customer_id,operation,idempotency_key,request_hash,created_at,expires_at) VALUES (?,'CREATE_CHECKOUT','abcdefghijklmnop',repeat('a',64),now(),now()+interval '1 day') ON CONFLICT DO NOTHING",
                customer);
    }
}
