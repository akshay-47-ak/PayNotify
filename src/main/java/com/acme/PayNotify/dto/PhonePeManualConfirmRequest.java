package com.acme.PayNotify.dto;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class PhonePeManualConfirmRequest extends ManualPaymentConfirmRequest {

    public PhonePeManualConfirmRequest(String utr, String payerName, String reason) {
        super(utr, payerName, reason);
    }
}
