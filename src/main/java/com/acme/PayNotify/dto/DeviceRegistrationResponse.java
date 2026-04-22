package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationResponse {

    private Long deviceId;
    private String enterpriseCode;
    private String enterpriseName;
    private String role;
    private String terminalId;
    private String deviceIdentifier;
    private String deviceName;
    private String status;
}