/*
 * File: EnterpriseValidationResponse.java
 * Created: 2026-04-22
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseValidationResponse {

    private boolean valid;
    private String enterpriseCode;
    private String enterpriseName;
    private String department;
    private Integer departmentCode;
    private String status;
    private String message;
}
