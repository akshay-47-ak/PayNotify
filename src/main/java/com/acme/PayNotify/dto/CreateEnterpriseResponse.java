package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEnterpriseResponse {

    private Long id;
    private String enterpriseCode;
    private String enterpriseName;
    private Boolean isActive;
    private Timestamp liveFrom;
    private Timestamp createdAt;
}