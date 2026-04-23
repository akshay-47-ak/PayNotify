package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEnterpriseRequest {

    private String enterpriseCode;
    private String enterpriseName;
    private Timestamp liveFrom;
}