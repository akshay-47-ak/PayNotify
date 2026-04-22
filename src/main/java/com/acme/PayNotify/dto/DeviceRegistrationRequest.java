package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceRegistrationRequest {

    private String enterpriseCode;
    private String role; // OWNER / CASHIER
    private String deviceIdentifier;
    private String deviceName;
}