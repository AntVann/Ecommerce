package com.marketflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizationTest {

    @Test
    void trimsAndCaseFoldsWithLocaleIndependentRules() {
        assertThat(IdentityService.normalizeEmail("  Customer.Example@EXAMPLE.COM  "))
                .isEqualTo("customer.example@example.com");
    }
}
