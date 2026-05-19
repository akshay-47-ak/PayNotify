package com.acme.PayNotify.dto;

import lombok.Data;

@Data
public class TerminalResponse {

    private Long deviceId;
    private String enterpriseCode;
    private String enterpriseName;
    private String terminalId;
    private String role;
    private String deviceIdentifier;
    private String deviceName;
}