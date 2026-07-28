/*
 * File: PaymentStatus.java
 * Created: 2026-07-10
 * Author: Akshay Athavale
 * Use: Defines shared constants/enums for PayNotify payment flows.
 */
package com.acme.PayNotify.type;

public enum PaymentStatus {
    PENDING("PENDING"),
    WAITING("WAITING"),
    PAID_AUTO_VERIFIED("PAID_AUTO_VERIFIED"),
    PHONEPE_MATCHED_WAITING_CONFIRMATION("PHONEPE_MATCHED_WAITING_CONFIRMATION"),
    PAID_CONFIRMED_BY_CASHIER("PAID_CONFIRMED_BY_CASHIER"),
    UNMATCHED_NOTIFICATION("UNMATCHED_NOTIFICATION"),
    AMBIGUOUS_NOTIFICATION("AMBIGUOUS_NOTIFICATION"),
    EXPIRED("EXPIRED"),
    CANCELLED_BY_CASHIER("CANCELLED_BY_CASHIER"),
    REJECTED_BY_CASHIER("REJECTED_BY_CASHIER"),
    DUPLICATE("DUPLICATE"),
    MATCHED_WAITING_CONFIRMATION("MATCHED_WAITING_CONFIRMATION"),
    USED_CONFIRMED("USED_CONFIRMED"),
    RECEIVED("RECEIVED"),
    PHONEPE_QUEUED("PHONEPE_QUEUED"),
    TRANSACTION_REF_NOT_FOUND("TRANSACTION_REF_NOT_FOUND"),
    PENDING_PAYMENT_NOT_FOUND("PENDING_PAYMENT_NOT_FOUND"),
    AMOUNT_MISMATCH("AMOUNT_MISMATCH"),
    PAYMENT_EXPIRED("PAYMENT_EXPIRED");

    private final String value;

    PaymentStatus(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean matches(String status) {
        return value.equals(status);
    }
}
