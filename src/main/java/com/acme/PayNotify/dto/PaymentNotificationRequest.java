package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentNotificationRequest {
    private String paymentId;
    private String packageName;
    private String title;
    private String message;
}
