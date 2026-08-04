package com.marketflow.catalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
    @Test
    void preservesDecimalMoneyWithoutFloatingPoint() {
        Money money = new Money(new BigDecimal("19.99"), "USD");
        assertThat(money.amount()).isEqualByComparingTo("19.99");
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    void rejectsNegativeAndExcessScale() {
        assertThatThrownBy(() -> new Money(new BigDecimal("-0.01"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(new BigDecimal("1.001"), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
