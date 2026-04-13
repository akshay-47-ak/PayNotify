package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Data

public class GenerateQrRequest {
    private String merchantName;
    private String upiId;
    private BigDecimal amount;
    private String transactionRef;
    private String note;
}
