package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEnterpriseRequest {

    private String enterpriseCode;
    private String enterpriseName;
    private String department;
    private Integer departmentCode;
    private LocalDateTime liveFrom;
}
