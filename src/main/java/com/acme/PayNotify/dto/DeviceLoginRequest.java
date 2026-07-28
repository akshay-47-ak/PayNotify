/*
 * File: DeviceLoginRequest.java
 * Created: 2026-05-18
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
package com.acme.PayNotify.dto;

import lombok.Data;

@Data
public class DeviceLoginRequest {

    private String deviceName;
    private String password;
}
