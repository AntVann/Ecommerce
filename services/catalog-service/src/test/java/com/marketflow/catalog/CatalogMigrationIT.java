package com.marketflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
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
class CatalogMigrationIT {
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
    void schemaEnforcesSellerScopedCanonicalSkuAndDecimalPrice() {
        UUID seller = UUID.randomUUID();
        UUID category = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID firstProduct = product(seller, category);
        UUID secondProduct = product(seller, category);
        variant(firstProduct, seller, "sku-1", "SKU-1", new BigDecimal("19.9900"));
        assertThatThrownBy(
                        () ->
                                variant(
                                        secondProduct,
                                        seller,
                                        " SKU-1 ",
                                        "SKU-1",
                                        new BigDecimal("20.0000")))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT price_amount FROM product_variant WHERE product_id=?",
                                BigDecimal.class,
                                firstProduct))
                .isEqualByComparingTo("19.9900");
    }

    private UUID product(UUID seller, UUID category) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO product(id,seller_id,category_id,title,description,status,created_at,updated_at) VALUES (?,?,?,'p','d','DRAFT',now(),now())",
                id,
                seller,
                category);
        return id;
    }

    private void variant(
            UUID product, UUID seller, String sku, String canonical, BigDecimal amount) {
        jdbc.update(
                "INSERT INTO product_variant(id,product_id,seller_id,sku,canonical_sku,name,price_amount,price_currency,created_at,updated_at) VALUES (?,?,?,?,?,'v',?,'USD',now(),now())",
                UUID.randomUUID(),
                product,
                seller,
                sku,
                canonical,
                amount);
    }
}
