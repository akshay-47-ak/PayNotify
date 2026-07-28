/*
 * File: PhonePeManualConfirmRequest.java
 * Created: 2026-07-27
 * Author: Akshay Athavale
 * Use: Defines request or response payloads exchanged by PayNotify API/WebSocket clients.
 */
package com.acme.PayNotify.dto;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PhonePeManualConfirmRequest extends ManualPaymentConfirmRequest {

    public PhonePeManualConfirmRequest(String utr, String payerName, String reason) {
        super(utr, payerName, reason);
    }
}
