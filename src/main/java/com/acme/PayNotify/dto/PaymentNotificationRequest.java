package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentNotificationRequest {

    private String enterpriseCode;
    private String deviceIdentifier;
    private String terminalId;
    private String appName;
    private String packageName;
    private String title;
    private String message;
    private String rawTitle;
    private String rawMessage;
    private BigDecimal amount;
    private String payerName;
    private String extractedTxnId;
    private Timestamp notificationReceivedAt;

    // optional direct value from flutter if already extracted
    private String transactionRef;
}
