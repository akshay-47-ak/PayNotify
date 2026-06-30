package com.acme.PayNotify.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PhonePeConfirmRequest {

    private Long cashierId;
    private Long notificationId;
}
