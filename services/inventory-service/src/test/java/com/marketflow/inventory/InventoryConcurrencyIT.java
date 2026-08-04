package com.marketflow.inventory;

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
class InventoryConcurrencyIT {
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
    void exactlyOneConcurrentReservationWinsFinalUnit() throws Exception {
        UUID variant = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_item(variant_id,seller_id,on_hand,reserved,created_at,updated_at) VALUES (?,?,1,0,now(),now())",
                variant,
                UUID.randomUUID());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> first = executor.submit(() -> reserve(start, variant));
            Future<Integer> second = executor.submit(() -> reserve(start, variant));
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT reserved FROM inventory_item WHERE variant_id=?",
                                Integer.class,
                                variant))
                .isOne();
    }

    private int reserve(CountDownLatch start, UUID variant) throws InterruptedException {
        start.await();
        return jdbc.update(
                "UPDATE inventory_item SET reserved=reserved+1,version=version+1 WHERE variant_id=? AND on_hand-reserved>=1",
                variant);
    }
}
