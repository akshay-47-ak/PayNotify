package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentNotificationRequest {
    private String packageName;
    private String title;
    private String message;

    // optional, if flutter extracts and sends directly
    private String transactionRef;
}