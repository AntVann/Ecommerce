package com.marketflow.catalog.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

public record Money(BigDecimal amount, String currency) {
    public Money {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Price must be non-negative.");
        }
        Currency unit = Currency.getInstance(currency);
        int scale = Math.max(unit.getDefaultFractionDigits(), 0);
        if (amount.scale() > scale || amount.precision() > 19) {
            throw new IllegalArgumentException("Price has an invalid precision.");
        }
        amount = amount.setScale(scale, RoundingMode.UNNECESSARY);
        currency = unit.getCurrencyCode();
    }
}
