package com.marketflow.catalog.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class CatalogRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public CatalogRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public List<Category> categories() {
        return jdbc.query(
                "SELECT id,code,name,parent_id FROM category WHERE active ORDER BY name",
                (rs, row) ->
                        new Category(
                                rs.getObject("id", UUID.class),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getObject("parent_id", UUID.class)));
    }

    public boolean categoryExists(UUID id) {
        Integer count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM category WHERE id=? AND active", Integer.class, id);
        return count != null && count == 1;
    }

    public Product create(
            UUID sellerId,
            UUID categoryId,
            String title,
            String description,
            Map<String, String> attributes,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO product(id,seller_id,category_id,title,description,status,attributes,created_at,updated_at) VALUES (?,?,?,?,?,'DRAFT',CAST(? AS jsonb),?,?)",
                id,
                sellerId,
                categoryId,
                title,
                description,
                json(attributes),
                db(now),
                db(now));
        return find(id).orElseThrow();
    }

    public Optional<Product> find(UUID id) {
        return jdbc
                .query(
                        "SELECT id,seller_id,category_id,title,description,status,attributes::text,version,created_at,updated_at,published_at FROM product WHERE id=?",
                        CatalogRepository::mapProduct,
                        id)
                .stream()
                .findFirst();
    }

    public List<Product> export(long offset, int limit) {
        return jdbc.query(
                "SELECT id,seller_id,category_id,title,description,status,attributes::text,version,created_at,updated_at,published_at FROM product WHERE status='ACTIVE' ORDER BY id OFFSET ? LIMIT ?",
                CatalogRepository::mapProduct,
                offset,
                limit);
    }

    public boolean sellerApproved(UUID sellerId) {
        return jdbc.queryForObject(
                        "SELECT count(*) FROM seller_projection WHERE seller_id=? AND status='APPROVED'",
                        Integer.class,
                        sellerId)
                == 1;
    }

    public List<CheckoutVariant> checkoutVariants(List<UUID> variantIds) {
        if (variantIds.isEmpty()) return List.of();
        return new NamedParameterJdbcTemplate(jdbc)
                .query(
                        """
                        SELECT v.id AS variant_id, v.product_id, v.seller_id, v.sku,
                               v.name AS variant_name, v.price_amount, v.price_currency,
                               v.active AS variant_active, v.version AS variant_version,
                               p.title AS product_name, p.status AS product_status,
                               p.version AS product_version,
                               COALESCE(sp.status, 'NOT_FOUND') AS seller_status
                        FROM product_variant v
                        JOIN product p ON p.id = v.product_id
                        LEFT JOIN seller_projection sp ON sp.seller_id = v.seller_id
                        WHERE v.id IN (:variantIds)
                        """,
                        new MapSqlParameterSource("variantIds", variantIds),
                        (rs, row) ->
                                new CheckoutVariant(
                                        rs.getObject("variant_id", UUID.class),
                                        rs.getObject("product_id", UUID.class),
                                        rs.getObject("seller_id", UUID.class),
                                        rs.getString("product_name"),
                                        rs.getString("variant_name"),
                                        rs.getString("sku"),
                                        rs.getBigDecimal("price_amount"),
                                        rs.getString("price_currency"),
                                        rs.getBoolean("variant_active"),
                                        rs.getString("product_status"),
                                        rs.getString("seller_status"),
                                        rs.getLong("product_version"),
                                        rs.getLong("variant_version")));
    }

    public void applySellerStatus(UUID eventId, UUID sellerId, String status, long version) {
        if (jdbc.update(
                        "INSERT INTO processed_message(consumer_name,event_id,processed_at) VALUES ('catalog-seller-v1',?,now()) ON CONFLICT DO NOTHING",
                        eventId)
                == 0) return;
        jdbc.update(
                "INSERT INTO seller_projection(seller_id,status,aggregate_version,updated_at) VALUES (?,?,?,now()) ON CONFLICT (seller_id) DO UPDATE SET status=EXCLUDED.status,aggregate_version=EXCLUDED.aggregate_version,updated_at=now() WHERE seller_projection.aggregate_version<EXCLUDED.aggregate_version",
                sellerId,
                status,
                version);
    }

    public boolean update(
            UUID id,
            long expectedVersion,
            UUID categoryId,
            String title,
            String description,
            Map<String, String> attributes,
            Instant now) {
        return jdbc.update(
                        "UPDATE product SET category_id=?,title=?,description=?,attributes=CAST(? AS jsonb),version=version+1,updated_at=? WHERE id=? AND version=? AND status<>'ARCHIVED'",
                        categoryId,
                        title,
                        description,
                        json(attributes),
                        db(now),
                        id,
                        expectedVersion)
                == 1;
    }

    public Variant addVariant(
            UUID productId,
            UUID sellerId,
            String sku,
            String name,
            Map<String, String> attributes,
            BigDecimal amount,
            String currency,
            Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO product_variant(id,product_id,seller_id,sku,canonical_sku,name,attributes,price_amount,price_currency,created_at,updated_at) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?)",
                    id,
                    productId,
                    sellerId,
                    sku,
                    canonicalSku(sku),
                    name,
                    json(attributes),
                    amount,
                    currency,
                    db(now),
                    db(now));
        } catch (DuplicateKeyException exception) {
            throw exception;
        }
        return variant(id).orElseThrow();
    }

    public Optional<Variant> variant(UUID id) {
        return jdbc
                .query(
                        "SELECT id,product_id,seller_id,sku,name,attributes::text,price_amount,price_currency,active,version FROM product_variant WHERE id=?",
                        CatalogRepository::mapVariant,
                        id)
                .stream()
                .findFirst();
    }

    public boolean updatePrice(
            UUID variantId, long expectedVersion, BigDecimal amount, String currency, Instant now) {
        return jdbc.update(
                        "UPDATE product_variant SET price_amount=?,price_currency=?,version=version+1,updated_at=? WHERE id=? AND version=?",
                        amount,
                        currency,
                        db(now),
                        variantId,
                        expectedVersion)
                == 1;
    }

    public List<Variant> variants(UUID productId) {
        return jdbc.query(
                "SELECT id,product_id,seller_id,sku,name,attributes::text,price_amount,price_currency,active,version FROM product_variant WHERE product_id=? ORDER BY created_at",
                CatalogRepository::mapVariant,
                productId);
    }

    public List<Image> images(UUID productId) {
        return jdbc.query(
                "SELECT id,product_id,object_key,content_type,byte_size,width,height,alt_text,display_order,status FROM product_image WHERE product_id=? ORDER BY display_order,id",
                CatalogRepository::mapImage,
                productId);
    }

    public Image addImage(
            UUID productId,
            String objectKey,
            String contentType,
            long size,
            int width,
            int height,
            String altText,
            int order,
            Instant now) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO product_image(id,product_id,object_key,content_type,byte_size,width,height,alt_text,display_order,status,created_at) VALUES (?,?,?,?,?,?,?,?,?,'READY',?)",
                id,
                productId,
                objectKey,
                contentType,
                size,
                width,
                height,
                altText,
                order,
                db(now));
        return images(productId).stream()
                .filter(image -> image.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    public boolean transition(
            UUID id,
            long expectedVersion,
            String fromStatus,
            String toStatus,
            UUID actor,
            String correlationId,
            Instant now) {
        int updated =
                jdbc.update(
                        "UPDATE product SET status=?,version=version+1,updated_at=?,published_at=CASE WHEN ?='ACTIVE' THEN ? ELSE published_at END WHERE id=? AND version=? AND status=?",
                        toStatus,
                        db(now),
                        toStatus,
                        db(now),
                        id,
                        expectedVersion,
                        fromStatus);
        if (updated == 1) {
            jdbc.update(
                    "INSERT INTO catalog_status_history(id,product_id,previous_status,new_status,actor_user_id,correlation_id,occurred_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    id,
                    fromStatus,
                    toStatus,
                    actor,
                    correlationId,
                    db(now));
        }
        return updated == 1;
    }

    public void outbox(
            String eventType,
            Product product,
            List<Variant> variants,
            List<Image> images,
            String correlationId,
            Instant now) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data =
                Map.of(
                        "productId",
                        product.id(),
                        "sellerId",
                        product.sellerId(),
                        "categoryId",
                        product.categoryId(),
                        "title",
                        product.title(),
                        "description",
                        product.description(),
                        "status",
                        product.status(),
                        "attributes",
                        map(product.attributesJson()),
                        "variants",
                        variants,
                        "images",
                        images);
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", eventId),
                        Map.entry("eventType", eventType),
                        Map.entry("aggregateType", "Product"),
                        Map.entry("aggregateId", product.id()),
                        Map.entry("aggregateVersion", product.version()),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "catalog-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                "INSERT INTO outbox_event(event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,payload,occurred_at,next_attempt_at) VALUES (?,?,'Product',?,?,?,CAST(? AS jsonb),?,?)",
                eventId,
                eventType,
                product.id(),
                product.version(),
                correlationId,
                json(envelope),
                db(now),
                db(now));
    }

    public void priceOutbox(Variant variant, String correlationId, Instant now) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data =
                Map.of(
                        "productId", variant.productId(),
                        "variantId", variant.id(),
                        "amount", variant.priceAmount().toPlainString(),
                        "currency", variant.priceCurrency());
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", eventId),
                        Map.entry("eventType", "catalog.price-changed.v1"),
                        Map.entry("aggregateType", "ProductVariant"),
                        Map.entry("aggregateId", variant.id()),
                        Map.entry("aggregateVersion", variant.version()),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlationId),
                        Map.entry("producer", "catalog-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                "INSERT INTO outbox_event(event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,payload,occurred_at,next_attempt_at) VALUES (?,'catalog.price-changed.v1','ProductVariant',?,?,?,CAST(? AS jsonb),?,?)",
                eventId,
                variant.id(),
                variant.version(),
                correlationId,
                json(envelope),
                db(now),
                db(now));
    }

    public Map<String, Object> map(String json) {
        try {
            return mapper.readValue(json, Map.class);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid catalog JSON", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialize catalog JSON", exception);
        }
    }

    public static String canonicalSku(String sku) {
        return sku.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static java.sql.Timestamp db(Instant value) {
        return java.sql.Timestamp.from(value);
    }

    private static Product mapProduct(ResultSet rs, int row) throws SQLException {
        return new Product(
                rs.getObject("id", UUID.class),
                rs.getObject("seller_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("attributes"),
                rs.getLong("version"),
                rs.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
                rs.getObject("published_at", java.time.OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("published_at", java.time.OffsetDateTime.class).toInstant());
    }

    private static Variant mapVariant(ResultSet rs, int row) throws SQLException {
        return new Variant(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getObject("seller_id", UUID.class),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("attributes"),
                rs.getBigDecimal("price_amount"),
                rs.getString("price_currency"),
                rs.getBoolean("active"),
                rs.getLong("version"));
    }

    private static Image mapImage(ResultSet rs, int row) throws SQLException {
        return new Image(
                rs.getObject("id", UUID.class),
                rs.getObject("product_id", UUID.class),
                rs.getString("object_key"),
                rs.getString("content_type"),
                rs.getLong("byte_size"),
                rs.getInt("width"),
                rs.getInt("height"),
                rs.getString("alt_text"),
                rs.getInt("display_order"),
                rs.getString("status"));
    }

    public record Category(UUID id, String code, String name, UUID parentId) {}

    public record Product(
            UUID id,
            UUID sellerId,
            UUID categoryId,
            String title,
            String description,
            String status,
            String attributesJson,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {}

    public record Variant(
            UUID id,
            UUID productId,
            UUID sellerId,
            String sku,
            String name,
            String attributesJson,
            BigDecimal priceAmount,
            String priceCurrency,
            boolean active,
            long version) {}

    public record Image(
            UUID id,
            UUID productId,
            String objectKey,
            String contentType,
            long byteSize,
            int width,
            int height,
            String altText,
            int displayOrder,
            String status) {}

    public record CheckoutVariant(
            UUID variantId,
            UUID productId,
            UUID sellerId,
            String productName,
            String variantName,
            String sku,
            BigDecimal priceAmount,
            String priceCurrency,
            boolean variantActive,
            String productStatus,
            String sellerStatus,
            long productVersion,
            long variantVersion) {}
}
