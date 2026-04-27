package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQrRequest {

    private String enterpriseCode;
    private String terminalId;
    private String merchantName;
    private String upiId;
    private BigDecimal amount;
    private String sourceApp;
}