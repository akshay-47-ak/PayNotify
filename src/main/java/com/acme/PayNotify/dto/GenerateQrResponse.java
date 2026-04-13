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
    private String upiUrl;
    private String qrImageBase64;
}
