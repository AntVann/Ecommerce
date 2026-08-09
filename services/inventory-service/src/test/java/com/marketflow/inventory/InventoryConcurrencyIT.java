package com.marketflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketflow.inventory.application.InventoryService;
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
    @Autowired InventoryService inventory;

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

    @Test
    void duplicateCompensationCommandsReleaseReservationOnlyOnce() {
        ReservationFixture fixture = reservation(4, 2);

        inventory.releaseOrderReservation(
                UUID.randomUUID(), fixture.reference(), "PAYMENT_DECLINED", "first");
        inventory.releaseOrderReservation(
                UUID.randomUUID(), fixture.reference(), "PAYMENT_DECLINED", "duplicate");

        assertThat(status(fixture.reference())).isEqualTo("RELEASED");
        assertThat(stock(fixture.variant())).containsExactly(4, 0);
        assertThat(movementCount(fixture.reference(), "RELEASE")).isOne();
        assertThat(eventCount("inventory.inventory-reservation-released.v1", fixture.reservation()))
                .isOne();
    }

    @Test
    void duplicateConfirmationCommandsCommitReservationOnlyOnce() {
        ReservationFixture fixture = reservation(4, 2);

        inventory.confirmOrderReservation(UUID.randomUUID(), fixture.reference(), "first");
        inventory.confirmOrderReservation(UUID.randomUUID(), fixture.reference(), "duplicate");

        assertThat(status(fixture.reference())).isEqualTo("CONFIRMED");
        assertThat(stock(fixture.variant())).containsExactly(2, 0);
        assertThat(movementCount(fixture.reference(), "COMMITMENT")).isOne();
        assertThat(
                        eventCount(
                                "inventory.inventory-reservation-confirmed.v1",
                                fixture.reservation()))
                .isOne();
    }

    @Test
    void confirmationAndCompensationCannotBothAdjustStock() throws Exception {
        ReservationFixture fixture = reservation(4, 2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> confirmation =
                    executor.submit(
                            () -> {
                                await(start);
                                inventory.confirmOrderReservation(
                                        UUID.randomUUID(), fixture.reference(), "confirmation");
                            });
            Future<?> release =
                    executor.submit(
                            () -> {
                                await(start);
                                inventory.releaseOrderReservation(
                                        UUID.randomUUID(),
                                        fixture.reference(),
                                        "PAYMENT_DECLINED",
                                        "compensation");
                            });
            start.countDown();
            confirmation.get();
            release.get();
        }

        String status = status(fixture.reference());
        assertThat(status).isIn("CONFIRMED", "RELEASED");
        assertThat(stock(fixture.variant())).containsExactly("CONFIRMED".equals(status) ? 2 : 4, 0);
        assertThat(
                        movementCount(fixture.reference(), "COMMITMENT")
                                + movementCount(fixture.reference(), "RELEASE"))
                .isOne();
    }

    private int reserve(CountDownLatch start, UUID variant) throws InterruptedException {
        start.await();
        return jdbc.update(
                "UPDATE inventory_item SET reserved=reserved+1,version=version+1 WHERE variant_id=? AND on_hand-reserved>=1",
                variant);
    }

    private ReservationFixture reservation(int onHand, int reserved) {
        UUID variant = UUID.randomUUID();
        UUID reference = UUID.randomUUID();
        UUID reservation = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_item(variant_id,seller_id,on_hand,reserved,created_at,updated_at) VALUES (?,?,?,?,now(),now())",
                variant,
                UUID.randomUUID(),
                onHand,
                reserved);
        jdbc.update(
                "INSERT INTO inventory_reservation(id,reference_id,status,expires_at,created_at,updated_at) VALUES (?,?,'PENDING',now()+interval '1 hour',now(),now())",
                reservation,
                reference);
        jdbc.update(
                "INSERT INTO inventory_reservation_line(reservation_id,variant_id,quantity) VALUES (?,?,?)",
                reservation,
                variant,
                reserved);
        return new ReservationFixture(reference, reservation, variant);
    }

    private String status(UUID reference) {
        return jdbc.queryForObject(
                "SELECT status FROM inventory_reservation WHERE reference_id=?",
                String.class,
                reference);
    }

    private java.util.List<Integer> stock(UUID variant) {
        return jdbc.queryForObject(
                "SELECT on_hand,reserved FROM inventory_item WHERE variant_id=?",
                (rs, row) -> java.util.List.of(rs.getInt("on_hand"), rs.getInt("reserved")),
                variant);
    }

    private int movementCount(UUID reference, String type) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM stock_movement WHERE reference_id=? AND movement_type=?",
                Integer.class,
                reference,
                type);
    }

    private int eventCount(String type, UUID aggregate) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type=? AND aggregate_id=?",
                Integer.class,
                type,
                aggregate);
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private record ReservationFixture(UUID reference, UUID reservation, UUID variant) {}
}
