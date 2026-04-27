package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentStatusEvent {

    private String paymentId;
    private String enterpriseCode;
    private String terminalId;
    private String status;
    private String transactionRef;
    private BigDecimal amount;
    private String payerName;
    private String utr;
    private String message;
    private Long timestamp;
    private String sourceApp;
}