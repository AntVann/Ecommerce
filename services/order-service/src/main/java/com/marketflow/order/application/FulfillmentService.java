package com.marketflow.order.application;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.FulfillmentModels.CreateShipmentCommand;
import com.marketflow.order.application.FulfillmentModels.ShipmentLineRequest;
import com.marketflow.order.application.FulfillmentModels.ShipmentView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FulfillmentService {
    private final OrderRepository repository;
    private final OrderSagaGateways gateways;

    public FulfillmentService(OrderRepository repository, OrderSagaGateways gateways) {
        this.repository = repository;
        this.gateways = gateways;
    }

    @Transactional
    public ShipmentView create(
            UUID user,
            UUID seller,
            UUID order,
            String key,
            String carrier,
            String tracking,
            List<ShipmentLineRequest> lines,
            String correlation) {
        gateways.requireFulfillmentPermission(seller, user);
        if (lines == null
                || lines.isEmpty()
                || lines.size() > 50
                || lines.stream()
                        .anyMatch(l -> l == null || l.orderItemId() == null || l.quantity() < 1))
            throw invalid("Shipment must contain one or more positive line quantities.");
        if (new HashSet<>(lines.stream().map(ShipmentLineRequest::orderItemId).toList()).size()
                != lines.size()) throw invalid("A shipment may contain each order line only once.");
        if (carrier == null
                || !carrier.matches("[A-Za-z0-9 ._-]{2,80}")
                || tracking == null
                || !tracking.matches("[A-Za-z0-9-]{3,120}"))
            throw invalid("Carrier and tracking number are invalid.");
        String hash = hash(order + "|" + seller + "|" + carrier + "|" + tracking + "|" + lines);
        var existingHash = repository.idempotencyHash(seller, key);
        if (existingHash.isPresent()) {
            if (!existingHash.get().equals(hash))
                throw conflict(
                        "SHIPMENT_IDEMPOTENCY_CONFLICT_409",
                        "The idempotency key was reused with different shipment data.");
            return repository
                    .idempotentShipment(seller, key)
                    .orElseThrow(
                            () ->
                                    conflict(
                                            "SHIPMENT_RETRY_409",
                                            "The shipment is still being created."));
        }
        Instant now = Instant.now();
        if (!repository.claimShipment(seller, key, hash, now))
            return repository
                    .idempotentShipment(seller, key)
                    .orElseThrow(
                            () ->
                                    conflict(
                                            "SHIPMENT_RETRY_409",
                                            "The shipment is still being created."));
        UUID shipment = UUID.randomUUID();
        repository.createShipment(
                new CreateShipmentCommand(order, seller, carrier, tracking, lines),
                shipment,
                correlation,
                now);
        repository.bindShipmentId(seller, key, shipment);
        return repository
                .shipment(shipment)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                        "SHIPMENT_CREATE_FAILED_500",
                                        "Shipment creation failed."));
    }

    @Transactional
    public ShipmentView transition(
            UUID user,
            UUID seller,
            UUID shipment,
            long expectedVersion,
            String target,
            String correlation) {
        gateways.requireFulfillmentPermission(seller, user);
        ShipmentView current =
                repository
                        .shipment(shipment)
                        .filter(s -> s.sellerId().equals(seller))
                        .orElseThrow(() -> notFound());
        if (current.version() != expectedVersion)
            throw conflict(
                    "SHIPMENT_VERSION_CONFLICT_409", "Shipment was changed by another request.");
        if (!allowed(current.status(), target))
            throw conflict(
                    "SHIPMENT_TRANSITION_INVALID_409", "Shipment status transition is invalid.");
        if (!repository.transitionShipment(
                seller, shipment, current.status(), target, correlation, Instant.now()))
            throw conflict(
                    "SHIPMENT_VERSION_CONFLICT_409", "Shipment was changed by another request.");
        return repository.shipment(shipment).orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> customerShipments(UUID customer, UUID order) {
        repository
                .owned(order, customer)
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "ORDER_NOT_FOUND_404",
                                        "Order was not found."));
        return repository.customerShipments(customer, order);
    }

    @Transactional(readOnly = true)
    public List<ShipmentView> sellerShipments(UUID user, UUID seller, UUID order) {
        gateways.requireSellerPermission(seller, user);
        return repository.sellerShipments(seller, order);
    }

    private boolean allowed(String current, String target) {
        return ("CREATED".equals(current) && "IN_TRANSIT".equals(target))
                || ("IN_TRANSIT".equals(current) && "DELIVERED".equals(target));
    }

    private ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "SHIPMENT_REQUEST_INVALID_400", message);
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private ApiException notFound() {
        return new ApiException(
                HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND_404", "Shipment was not found.");
    }

    private static String hash(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
