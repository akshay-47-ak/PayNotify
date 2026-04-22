package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationResponse {

    private boolean matched;
    private String status;
    private String paymentId;
    private String transactionRef;
    private BigDecimal expectedAmount;
    private String receivedAmount;
    private boolean amountMatched;
    private String utr;
    private String payerName;
    private String message;
}