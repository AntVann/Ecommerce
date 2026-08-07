package com.marketflow.order.application;

import com.marketflow.order.application.CheckoutModels.CheckoutCommand;
import com.marketflow.order.application.CheckoutModels.OrderItem;
import com.marketflow.order.application.CheckoutModels.OrderView;
import com.marketflow.order.domain.Address;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public boolean claim(UUID customer, String operation, String key, String hash, Instant now) {
        return jdbc.update(
                        "INSERT INTO idempotency_record(customer_id,operation,idempotency_key,request_hash,created_at,expires_at) VALUES (?,?,?,?,?,?+interval '24 hours') ON CONFLICT DO NOTHING",
                        customer,
                        operation,
                        key,
                        hash,
                        db(now),
                        db(now))
                == 1;
    }

    public Optional<Idempotency> idempotency(UUID customer, String op, String key) {
        return jdbc
                .query(
                        "SELECT request_hash,order_id,http_status FROM idempotency_record WHERE customer_id=? AND operation=? AND idempotency_key=?",
                        (r, n) ->
                                new Idempotency(
                                        r.getString(1),
                                        r.getObject(2, UUID.class),
                                        (Integer) r.getObject(3)),
                        customer,
                        op,
                        key)
                .stream()
                .findFirst();
    }

    public void complete(
            UUID customer, String op, String key, UUID orderId, int status, Object response) {
        jdbc.update(
                "UPDATE idempotency_record SET order_id=?,http_status=?,response_payload=CAST(? AS jsonb) WHERE customer_id=? AND operation=? AND idempotency_key=?",
                orderId,
                status,
                json(response),
                customer,
                op,
                key);
    }

    public void lockCart(UUID customer, UUID cart, long version) {
        jdbc.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                Long.class,
                customer + ":" + cart + ":" + version);
    }

    public void create(
            UUID id,
            UUID customer,
            CheckoutCommand c,
            String currency,
            BigDecimal subtotal,
            Address shipping,
            Address billing,
            Instant now) {
        jdbc.update(
                "INSERT INTO customer_order(id,customer_id,cart_id,cart_version,status,currency,subtotal,grand_total,shipping_address,billing_address,created_at,updated_at) VALUES (?,?,?,?,'PENDING',?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)",
                id,
                customer,
                c.cartId(),
                c.cartVersion(),
                currency,
                subtotal,
                subtotal,
                json(shipping),
                json(billing),
                db(now),
                db(now));
    }

    public void item(UUID orderId, OrderItem i) {
        jdbc.update(
                "INSERT INTO order_item(id,order_id,seller_id,product_id,variant_id,product_name,variant_name,sku,quantity,unit_price,currency,line_subtotal,catalog_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                i.id(),
                orderId,
                i.sellerId(),
                i.productId(),
                i.variantId(),
                i.productName(),
                i.variantName(),
                i.sku(),
                i.quantity(),
                i.unitPrice(),
                i.currency(),
                i.lineSubtotal(),
                i.catalogVersion());
    }

    public void startSaga(UUID orderId, int expected, Instant deadline, Instant now) {
        jdbc.update(
                "INSERT INTO order_saga(order_id,expected_lines,state,deadline_at,created_at,updated_at) VALUES (?,?,'AWAITING_INVENTORY',?,?,?)",
                orderId,
                expected,
                db(deadline),
                db(now),
                db(now));
    }

    public void history(
            UUID order,
            String previous,
            String next,
            String reason,
            String correlation,
            Instant now) {
        jdbc.update(
                "INSERT INTO order_status_history(id,order_id,previous_status,new_status,reason,correlation_id,occurred_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                order,
                previous,
                next,
                reason,
                correlation,
                db(now));
    }

    public Optional<OrderView> order(UUID id) {
        return jdbc.query("SELECT * FROM customer_order WHERE id=?", this::mapOrder, id).stream()
                .findFirst();
    }

    public Optional<OrderView> owned(UUID id, UUID customer) {
        return jdbc
                .query(
                        "SELECT * FROM customer_order WHERE id=? AND customer_id=?",
                        this::mapOrder,
                        id,
                        customer)
                .stream()
                .findFirst();
    }

    public Optional<OrderView> cartOrder(UUID customer, UUID cart, long version) {
        return jdbc
                .query(
                        "SELECT * FROM customer_order WHERE customer_id=? AND cart_id=? AND cart_version=?",
                        this::mapOrder,
                        customer,
                        cart,
                        version)
                .stream()
                .findFirst();
    }

    public void orderCreatedOutbox(OrderView order, long ttl, String correlation, Instant now) {
        List<Map<String, Object>> lines =
                order.items().stream()
                        .map(
                                i ->
                                        Map.<String, Object>of(
                                                "variantId",
                                                i.variantId(),
                                                "quantity",
                                                i.quantity()))
                        .toList();
        outbox(
                "order.order-created.v1",
                order.id(),
                order.version(),
                correlation,
                Map.of("orderId", order.id(), "reservationTtlSeconds", ttl, "lines", lines),
                now);
    }

    private void outbox(
            String type,
            UUID id,
            long version,
            String correlation,
            Map<String, Object> data,
            Instant now) {
        UUID event = UUID.randomUUID();
        Map<String, Object> envelope =
                Map.ofEntries(
                        Map.entry("eventId", event),
                        Map.entry("eventType", type),
                        Map.entry("aggregateType", "Order"),
                        Map.entry("aggregateId", id),
                        Map.entry("aggregateVersion", version),
                        Map.entry("occurredAt", now),
                        Map.entry("correlationId", correlation),
                        Map.entry("producer", "order-service"),
                        Map.entry("schemaVersion", 1),
                        Map.entry("data", data));
        jdbc.update(
                "INSERT INTO outbox_event(event_id,event_type,aggregate_type,aggregate_id,aggregate_version,correlation_id,payload,occurred_at,next_attempt_at) VALUES (?,?,'Order',?,?,?,CAST(? AS jsonb),?,?)",
                event,
                type,
                id,
                version,
                correlation,
                json(envelope),
                db(now),
                db(now));
    }

    public boolean processed(String consumer, UUID event) {
        return jdbc.update(
                        "INSERT INTO processed_message(consumer_name,event_id,processed_at) VALUES (?,?,now()) ON CONFLICT DO NOTHING",
                        consumer,
                        event)
                == 0;
    }

    public Saga sagaForUpdate(UUID order) {
        return jdbc.queryForObject(
                "SELECT expected_lines,reserved_lines,state FROM order_saga WHERE order_id=? FOR UPDATE",
                (r, n) -> new Saga(r.getInt(1), r.getInt(2), r.getString(3)),
                order);
    }

    public boolean recordOutcome(
            UUID order, UUID variant, String outcome, UUID event, Instant now) {
        return jdbc.update(
                        "INSERT INTO order_inventory_progress(order_id,variant_id,outcome,event_id,occurred_at) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",
                        order,
                        variant,
                        outcome,
                        event,
                        db(now))
                == 1;
    }

    public int incrementReserved(UUID order, Instant now) {
        jdbc.update(
                "UPDATE order_saga SET reserved_lines=reserved_lines+1,version=version+1,updated_at=? WHERE order_id=? AND state='AWAITING_INVENTORY'",
                db(now),
                order);
        return jdbc.queryForObject(
                "SELECT reserved_lines FROM order_saga WHERE order_id=?", Integer.class, order);
    }

    public boolean transition(
            UUID order, String from, String to, String reason, String correlation, Instant now) {
        int n =
                jdbc.update(
                        "UPDATE customer_order SET status=?,cancellation_reason=?,version=version+1,updated_at=? WHERE id=? AND status=?",
                        to,
                        reason,
                        db(now),
                        order,
                        from);
        if (n == 1) {
            jdbc.update(
                    "UPDATE order_saga SET state=?,version=version+1,updated_at=? WHERE order_id=?",
                    to,
                    db(now),
                    order);
            history(order, from, to, reason, correlation, now);
        }
        return n == 1;
    }

    public List<UUID> stale(Instant now) {
        return jdbc.query(
                "SELECT order_id FROM order_saga WHERE state='AWAITING_INVENTORY' AND deadline_at<=? ORDER BY deadline_at LIMIT 50",
                (r, n) -> r.getObject(1, UUID.class),
                db(now));
    }

    private OrderView mapOrder(ResultSet r, int row) throws SQLException {
        UUID id = r.getObject("id", UUID.class);
        return new OrderView(
                id,
                r.getObject("customer_id", UUID.class),
                r.getObject("cart_id", UUID.class),
                r.getLong("cart_version"),
                r.getString("status"),
                r.getString("cancellation_reason"),
                r.getString("currency"),
                r.getBigDecimal("subtotal"),
                r.getBigDecimal("tax_total"),
                r.getBigDecimal("shipping_total"),
                r.getBigDecimal("discount_total"),
                r.getBigDecimal("grand_total"),
                read(r.getString("shipping_address"), Address.class),
                read(r.getString("billing_address"), Address.class),
                r.getLong("version"),
                r.getObject("created_at", java.time.OffsetDateTime.class).toInstant(),
                r.getObject("updated_at", java.time.OffsetDateTime.class).toInstant(),
                items(id));
    }

    private List<OrderItem> items(UUID order) {
        return jdbc.query(
                "SELECT * FROM order_item WHERE order_id=? ORDER BY id",
                (r, n) ->
                        new OrderItem(
                                r.getObject("id", UUID.class),
                                r.getObject("seller_id", UUID.class),
                                r.getObject("product_id", UUID.class),
                                r.getObject("variant_id", UUID.class),
                                r.getString("product_name"),
                                r.getString("variant_name"),
                                r.getString("sku"),
                                r.getInt("quantity"),
                                r.getBigDecimal("unit_price"),
                                r.getString("currency"),
                                r.getBigDecimal("line_subtotal"),
                                r.getLong("catalog_version")),
                order);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to serialize order data", e);
        }
    }

    private <T> T read(String s, Class<T> type) {
        try {
            return mapper.readValue(s, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Unable to read order snapshot", e);
        }
    }

    private static Timestamp db(Instant i) {
        return Timestamp.from(i);
    }

    public record Idempotency(String requestHash, UUID orderId, Integer status) {}

    public record Saga(int expected, int reserved, String state) {}
}
