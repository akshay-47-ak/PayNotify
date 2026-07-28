/*
 * File: DeviceRegistrationRequest.java
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
public class DeviceRegistrationRequest {

    private String enterpriseCode;
    private String role; // OWNER / CASHIER
    private String deviceIdentifier;
    private String deviceName;
    private String password;
}
