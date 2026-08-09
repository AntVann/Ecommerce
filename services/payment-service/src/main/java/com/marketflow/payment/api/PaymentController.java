package com.marketflow.payment.api;

import com.marketflow.payment.application.PaymentModels.AuthorizationCommand;
import com.marketflow.payment.application.PaymentModels.CallbackCommand;
import com.marketflow.payment.application.PaymentModels.PaymentView;
import com.marketflow.payment.application.PaymentService;
import com.marketflow.payment.infrastructure.provider.FakeProviderProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/payments")
public class PaymentController {
    private final PaymentService service;
    private final FakeProviderProperties providerProperties;

    public PaymentController(PaymentService service, FakeProviderProperties providerProperties) {
        this.service = service;
        this.providerProperties = providerProperties;
    }

    @PostMapping("/authorizations")
    public ResponseEntity<PaymentView> authorize(
            @Valid @RequestBody AuthorizationCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        PaymentView result = service.authorize(command, idempotencyKey, correlation(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/callbacks/fake")
    public PaymentView callback(
            @Valid @RequestBody CallbackCommand command,
            @RequestHeader("X-Fake-Provider-Signature") String signature,
            HttpServletRequest request) {
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                providerProperties.callbackSignature().getBytes(StandardCharsets.UTF_8))) {
            throw new PaymentService.PaymentException("PAYMENT_CALLBACK_SIGNATURE_INVALID");
        }
        return service.callback(command, correlation(request));
    }

    @PostMapping("/{paymentId}/reconciliation")
    public PaymentView reconcile(
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        return service.reconcile(paymentId, idempotencyKey, correlation(request));
    }

    private static String correlation(HttpServletRequest request) {
        Object value = request.getAttribute("correlationId");
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }
}
