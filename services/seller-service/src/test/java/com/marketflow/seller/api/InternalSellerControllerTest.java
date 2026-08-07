package com.marketflow.seller.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.marketflow.seller.application.SellerRepository;
import com.marketflow.seller.infrastructure.security.SellerSecurityProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalSellerControllerTest {
    @Mock SellerRepository repository;

    @Test
    void batchStatusIncludesMissingSellerAndRequiresServiceKey() {
        UUID approved = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(repository.findSeller(approved, false))
                .thenReturn(
                        Optional.of(
                                new SellerRepository.SellerRecord(
                                        approved,
                                        UUID.randomUUID(),
                                        "Store",
                                        "Store LLC",
                                        "US",
                                        "APPROVED",
                                        3,
                                        Instant.EPOCH,
                                        Instant.EPOCH)));
        when(repository.findSeller(missing, false)).thenReturn(Optional.empty());
        var controller =
                new InternalSellerController(
                        repository,
                        new SellerSecurityProperties("identity", "issuer", "audience", "secret"));
        var request =
                new InternalSellerController.StatusValidationRequest(List.of(approved, missing));

        var results = controller.statuses("secret", request);

        assertThat(results)
                .extracting(InternalSellerController.StatusResponse::status)
                .containsExactly("APPROVED", "NOT_FOUND");
        assertThatThrownBy(() -> controller.statuses("wrong", request))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo("INTERNAL_AUTHENTICATION_401");
    }
}
