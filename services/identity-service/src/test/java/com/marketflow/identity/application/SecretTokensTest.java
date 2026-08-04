package com.marketflow.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretTokensTest {

    @Test
    void createsDistinctHighEntropyOpaqueTokensAndStableDigests() {
        String first = SecretTokens.random();
        String second = SecretTokens.random();

        assertThat(first).hasSizeGreaterThanOrEqualTo(43).isNotEqualTo(second);
        assertThat(SecretTokens.digest(first)).hasSize(64).isEqualTo(SecretTokens.digest(first));
        assertThat(SecretTokens.equal(SecretTokens.digest(first), SecretTokens.digest(first)))
                .isTrue();
        assertThat(SecretTokens.equal(SecretTokens.digest(first), SecretTokens.digest(second)))
                .isFalse();
    }
}
