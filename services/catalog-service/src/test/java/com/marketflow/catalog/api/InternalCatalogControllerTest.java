package com.marketflow.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.marketflow.catalog.application.CatalogService;
import com.marketflow.catalog.infrastructure.security.CatalogSecurityProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalCatalogControllerTest {
    @Mock CatalogService catalog;

    @Test
    void checkoutValidationRequiresServiceKeyAndDelegatesBatch() {
        UUID variant = UUID.randomUUID();
        var expected =
                List.of(
                        new CatalogService.CheckoutValidation(
                                variant,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                "NOT_FOUND",
                                null,
                                null));
        when(catalog.validateCheckout(List.of(variant))).thenReturn(expected);
        var controller = new CatalogController(catalog, properties());
        var request = new CatalogController.CheckoutValidationRequest(List.of(variant));

        assertThat(controller.checkoutValidations("secret", request)).isEqualTo(expected);
        assertThatThrownBy(() -> controller.checkoutValidations("wrong", request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INTERNAL_AUTHENTICATION_401");
    }

    private static CatalogSecurityProperties properties() {
        return new CatalogSecurityProperties("identity", "issuer", "audience", "seller", "secret");
    }
}
