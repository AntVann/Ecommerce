package com.marketflow.order.domain;

public enum OrderState {
    PENDING,
    INVENTORY_RESERVED,
    CANCELLED,
    MANUAL_REVIEW
}
