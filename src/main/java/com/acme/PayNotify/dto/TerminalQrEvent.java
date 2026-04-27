package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TerminalQrEvent {

    private String paymentId;
    private String enterpriseCode;
    private String terminalId;
    private String transactionRef;
    private BigDecimal amount;
    private String merchantName;
    private String upiId;
    private String upiUrl;
    private String qrImageBase64;
    private String status;
    private String message;
    private Long timestamp;
    private String sourceApp;
}