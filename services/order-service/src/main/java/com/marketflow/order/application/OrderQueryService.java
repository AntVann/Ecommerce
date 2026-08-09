package com.marketflow.order.application;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.CheckoutModels.OrderPage;
import com.marketflow.order.application.CheckoutModels.SellerOrderPage;
import com.marketflow.order.application.CheckoutModels.SellerOrderView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderQueryService {
    private final OrderRepository repository;
    private final OrderSagaGateways gateways;

    public OrderQueryService(OrderRepository repository, OrderSagaGateways gateways) {
        this.repository = repository;
        this.gateways = gateways;
    }

    @Transactional(readOnly = true)
    public OrderPage customerHistory(UUID customer, String cursor, int requestedLimit) {
        Cursor position = decode(cursor);
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        var rows = repository.customerOrders(customer, position.time(), position.id(), limit + 1);
        boolean more = rows.size() > limit;
        var items = more ? rows.subList(0, limit) : rows;
        return new OrderPage(
                items, more ? encode(items.getLast().createdAt(), items.getLast().id()) : null);
    }

    public SellerOrderPage sellerHistory(
            UUID user, UUID seller, String cursor, int requestedLimit) {
        gateways.requireSellerPermission(seller, user);
        Cursor position = decode(cursor);
        int limit = Math.min(Math.max(requestedLimit, 1), 100);
        var rows = repository.sellerOrders(seller, position.time(), position.id(), limit + 1);
        boolean more = rows.size() > limit;
        var items = more ? rows.subList(0, limit) : rows;
        return new SellerOrderPage(
                items, more ? encode(items.getLast().createdAt(), items.getLast().id()) : null);
    }

    public SellerOrderView sellerOrder(UUID user, UUID seller, UUID order) {
        gateways.requireSellerPermission(seller, user);
        return repository.sellerOrder(seller, order).orElseThrow(this::notFound);
    }

    private static String encode(Instant time, UUID id) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString((time + "|" + id).getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String value) {
        if (value == null || value.isBlank()) return new Cursor(null, null);
        try {
            String decoded =
                    new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            int separator = decoded.indexOf('|');
            return new Cursor(
                    Instant.parse(decoded.substring(0, separator)),
                    UUID.fromString(decoded.substring(separator + 1)));
        } catch (RuntimeException e) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "ORDER_CURSOR_INVALID_400", "Order cursor is invalid.");
        }
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND_404", "Order was not found.");
    }

    private record Cursor(Instant time, UUID id) {}
}
