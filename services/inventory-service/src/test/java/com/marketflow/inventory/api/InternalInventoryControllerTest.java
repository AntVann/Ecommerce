package com.marketflow.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.marketflow.inventory.application.InventoryRepository;
import com.marketflow.inventory.application.InventoryService;
import com.marketflow.inventory.infrastructure.security.InventorySecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalInventoryControllerTest {
    @Mock InventoryService inventory;

    @Test
    void availabilityRequiresServiceKeyAndDelegatesBatch() {
        UUID variant = UUID.randomUUID();
        var expected =
                List.of(
                        new InventoryRepository.Item(
                                variant, UUID.randomUUID(), 10, 2, 4, Instant.EPOCH));
        when(inventory.availability(List.of(variant))).thenReturn(expected);
        var controller =
                new InventoryController(
                        inventory,
                        new InventorySecurityProperties(
                                "identity", "issuer", "audience", "seller", "secret"));
        var request = new InventoryController.AvailabilityRequest(List.of(variant));

        assertThat(controller.availability("secret", request)).isEqualTo(expected);
        assertThatThrownBy(() -> controller.availability("wrong", request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INTERNAL_AUTHENTICATION_401");
    }

    @Test
    void confirmationRequiresServiceKeyAndReturnsTerminalReservation() {
        UUID reference = UUID.randomUUID();
        var expected =
                new InventoryRepository.Reservation(
                        UUID.randomUUID(),
                        reference,
                        "CONFIRMED",
                        Instant.EPOCH,
                        Instant.EPOCH,
                        Instant.EPOCH);
        when(inventory.confirm(reference, "unknown")).thenReturn(expected);
        var controller =
                new InventoryController(
                        inventory,
                        new InventorySecurityProperties(
                                "identity", "issuer", "audience", "seller", "secret"));

        assertThat(controller.confirm("secret", reference)).isEqualTo(expected);
        assertThatThrownBy(() -> controller.confirm("wrong", reference))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INTERNAL_AUTHENTICATION_401");
    }
}
