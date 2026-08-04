package com.marketflow.seller.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SellerServiceTest {

    @Test
    void requestHashIsStableAndSensitiveToInput() {
        assertThat(SellerService.digest("seller:1:APPROVED"))
                .hasSize(64)
                .isEqualTo(SellerService.digest("seller:1:APPROVED"))
                .isNotEqualTo(SellerService.digest("seller:2:APPROVED"));
    }
}
