/*
 * File: PaymentEventType.java
 * Created: 2026-07-10
 * Author: Akshay Athavale
 * Use: Defines shared constants/enums for PayNotify payment flows.
 */
package com.acme.PayNotify.type;

public enum PaymentEventType {
    PAYMENT_SUCCESS("PAYMENT_SUCCESS"),
    PHONEPE_PAYMENT_CONFIRMATION_REQUIRED("PHONEPE_PAYMENT_CONFIRMATION_REQUIRED"),
    PAYMENT_STATUS_UPDATED("PAYMENT_STATUS_UPDATED");

    private final String value;

    PaymentEventType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
