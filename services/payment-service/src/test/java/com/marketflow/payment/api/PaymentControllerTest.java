package com.marketflow.payment.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.marketflow.payment.application.PaymentModels.CallbackCommand;
import com.marketflow.payment.application.PaymentService;
import com.marketflow.payment.domain.PaymentStatus;
import com.marketflow.payment.infrastructure.provider.FakeProviderProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentControllerTest {
    private final PaymentController controller =
            new PaymentController(
                    mock(PaymentService.class),
                    new FakeProviderProperties(Duration.ZERO, 2, "valid-signature", true));

    @Test
    void rejectsCallbackWithInvalidSignatureBeforeCallingApplication() {
        CallbackCommand command =
                new CallbackCommand(
                        UUID.randomUUID(), "provider-reference", PaymentStatus.AUTHORIZED, null);

        assertThatThrownBy(
                        () ->
                                controller.callback(
                                        command,
                                        "invalid-signature",
                                        mock(HttpServletRequest.class)))
                .isInstanceOf(PaymentService.PaymentException.class)
                .hasMessage("PAYMENT_CALLBACK_SIGNATURE_INVALID");
    }
}
