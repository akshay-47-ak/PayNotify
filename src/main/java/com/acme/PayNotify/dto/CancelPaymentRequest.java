/*
 * File: CancelPaymentRequest.java
 * Created: 2026-07-28
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
public class CancelPaymentRequest {

    private String reason;
}
