package com.marketflow.order.domain;

public enum OrderState {
    PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PROCESSING,
    CONFIRMED,
    PAYMENT_FAILED,
    CANCELLED,
    MANUAL_REVIEW
}
