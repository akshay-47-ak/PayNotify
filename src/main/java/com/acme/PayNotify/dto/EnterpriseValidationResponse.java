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
    private String status;
    private String message;
}