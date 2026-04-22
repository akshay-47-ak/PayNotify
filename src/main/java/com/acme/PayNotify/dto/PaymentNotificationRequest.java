package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationRequest {

    private String enterpriseCode;
    private String deviceIdentifier;
    private String packageName;
    private String title;
    private String message;

    // optional direct value from flutter if already extracted
    private String transactionRef;
}