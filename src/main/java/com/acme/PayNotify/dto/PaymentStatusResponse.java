/*
 * File: PaymentStatusResponse.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusResponse {

    private String paymentId;
    private String enterpriseCode;
    private String terminalId;
    private String transactionRef;
    private BigDecimal amount;
    private String status;
    private String payerName;
    private String utr;
    private Long notificationId;
    private String message;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
