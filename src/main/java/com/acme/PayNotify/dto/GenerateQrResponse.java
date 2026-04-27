package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQrResponse {

    private String paymentId;
    private String transactionRef;
    private String terminalId;
    private String upiUrl;
    private String qrImageBase64;
    private String status;
    private String sourceApp;
}