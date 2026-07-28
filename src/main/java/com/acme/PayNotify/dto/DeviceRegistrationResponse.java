/*
 * File: DeviceRegistrationResponse.java
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
public class DeviceRegistrationResponse {

    private Long deviceId;
    private String enterpriseCode;
    private String enterpriseName;
    private String role;
    private String terminalId;
    private String deviceIdentifier;
    private String deviceName;
    private String status;
    private String token;
    private Long tokenExpiresAt;
    private String tokenType;
}
