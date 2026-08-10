package com.marketflow.inventory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.marketflow.inventory.application.InventoryRepository;
import com.marketflow.inventory.application.InventoryService;
import com.marketflow.inventory.infrastructure.security.InventorySecurityProperties;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PublicAvailabilityControllerTest {
    @Mock InventoryService inventory;

    @Test
    void publicAvailabilityDoesNotExposeSellerInventoryFields() {
        UUID variant = UUID.randomUUID();
        var expected = new InventoryRepository.PublicAvailability(variant, 7, Instant.EPOCH);
        when(inventory.publicAvailability(variant)).thenReturn(expected);
        var controller =
                new InventoryController(
                        inventory,
                        new InventorySecurityProperties(
                                "identity", "issuer", "audience", "seller", "secret"));

        assertThat(controller.publicAvailability(variant)).isEqualTo(expected);
    }
}
