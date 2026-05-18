package com.acme.PayNotify.dto;

import lombok.Data;

@Data
public class DeviceLoginRequest {

    private String enterpriseCode;
    private String deviceIdentifier;
}