package com.marketflow.payment.domain;

public enum PaymentStatus {
    CREATED,
    PROCESSING,
    AUTHORIZED,
    DECLINED,
    FAILED,
    UNKNOWN
}
