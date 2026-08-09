package com.marketflow.order.infrastructure.client;

import com.marketflow.order.api.ApiException;
import com.marketflow.order.application.OrderSagaGateways;
import com.marketflow.order.infrastructure.security.OrderProperties;
import com.marketflow.order.infrastructure.security.PaymentIntegrationProperties;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public final class HttpOrderSagaGateways implements OrderSagaGateways {
    private final OrderProperties order;
    private final PaymentIntegrationProperties payment;
    private final RestClient.Builder clients;

    public HttpOrderSagaGateways(
            OrderProperties order,
            PaymentIntegrationProperties payment,
            RestClient.Builder clients) {
        this.order = order;
        this.payment = payment;
        this.clients = clients;
    }

    @Override
    public void authorizePayment(
            UUID orderId,
            UUID customerId,
            BigDecimal amount,
            String currency,
            String fakePaymentToken,
            String idempotencyKey) {
        try {
            clients.baseUrl(payment.baseUrl())
                    .build()
                    .post()
                    .uri("/internal/v1/payments/authorizations")
                    .header("X-Internal-Service-Key", payment.internalServiceKey())
                    .header("Idempotency-Key", idempotencyKey)
                    .body(
                            Map.of(
                                    "orderId", orderId,
                                    "customerId", customerId,
                                    "amount", amount.toPlainString(),
                                    "currency", currency,
                                    "fakePaymentToken", fakePaymentToken))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            unavailable("Payment authorization initiation is unavailable.");
        }
    }

    @Override
    public void confirmInventory(UUID orderId) {
        inventoryCommand(orderId, "confirm");
    }

    @Override
    public void releaseInventory(UUID orderId) {
        inventoryCommand(orderId, "release");
    }

    @Override
    public void requireSellerPermission(UUID sellerId, UUID userId) {
        try {
            Authorization response =
                    clients.baseUrl(order.sellerBaseUrl())
                            .build()
                            .get()
                            .uri(
                                    "/internal/v1/sellers/{sellerId}/authorization?userId={userId}&permission=ORDER_READ",
                                    sellerId,
                                    userId)
                            .header("X-Internal-Service-Key", order.internalServiceKey())
                            .retrieve()
                            .body(Authorization.class);
            if (response == null || !response.authorized())
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "SELLER_ORDER_ACCESS_DENIED_403",
                        "Seller order access was denied.");
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            unavailable("Seller authorization is unavailable.");
        }
    }

    private void inventoryCommand(UUID orderId, String command) {
        try {
            clients.baseUrl(order.inventoryBaseUrl())
                    .build()
                    .post()
                    .uri(
                            "/internal/v1/inventory/reservations/{orderId}/{command}",
                            orderId,
                            command)
                    .header("X-Internal-Service-Key", order.internalServiceKey())
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            unavailable("Inventory reservation command is unavailable.");
        }
    }

    private static void unavailable(String message) {
        throw new ApiException(
                HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE_503", message);
    }

    private record Authorization(boolean authorized) {}
}
