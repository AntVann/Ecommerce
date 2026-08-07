package com.marketflow.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class InventoryMigrationIT {
    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Test
    void migratesActiveReservationsToPendingAndKeepsBothStatusesCompatible() {
        Flyway first = flyway(MigrationVersion.fromVersion("1"));
        first.migrate();
        var jdbc =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        UUID reference = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO inventory_reservation(id,reference_id,status,expires_at,created_at,updated_at) VALUES (?,?,'ACTIVE',now()+interval '1 hour',now(),now())",
                UUID.randomUUID(),
                reference);

        Flyway latest = flyway(null);
        assertThat(latest.migrate().migrationsExecuted).isOne();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM inventory_reservation WHERE reference_id=?",
                                String.class,
                                reference))
                .isEqualTo("PENDING");
        jdbc.update(
                "UPDATE inventory_reservation SET status='ACTIVE' WHERE reference_id=?", reference);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM inventory_reservation WHERE reference_id=?",
                                String.class,
                                reference))
                .isEqualTo("ACTIVE");
    }

    private static Flyway flyway(MigrationVersion target) {
        var config =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration");
        if (target != null) config.target(target);
        return config.load();
    }
}
