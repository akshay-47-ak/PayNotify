/*
 * File: PaymentApp.java
 * Created: 2026-07-10
 * Author: Akshay Athavale
 * Use: Defines shared constants/enums for PayNotify payment flows.
 */
package com.acme.PayNotify.type;

public enum PaymentApp {
    GOOGLE_PAY("GOOGLE_PAY"),
    PHONEPE("PHONEPE"),
    UNKNOWN("UNKNOWN");

    private final String value;

    PaymentApp(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean matches(String app) {
        return value.equals(app);
    }
}
