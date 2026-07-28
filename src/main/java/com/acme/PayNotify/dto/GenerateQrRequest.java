/*
 * File: GenerateQrRequest.java
 * Created: 2026-04-13
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
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
    private Long documentOwnCode;
}
