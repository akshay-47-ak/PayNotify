/*
 * File: PaymentWebSocketService.java
 * Created: 2026-04-18
 * Author: Akshay Athavale
 * Use: Publishes WebSocket events for Web cashier screens and Mobile terminal listeners.
 */
package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.PaymentStatusEvent;
import com.acme.PayNotify.dto.TerminalQrEvent;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentRequest;
import com.acme.PayNotify.type.PaymentEventType;
import com.acme.PayNotify.type.PaymentStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaymentWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // WebSocket to Mobile terminal: publishes QR payloads on /topic/terminal/{terminalId}.
    public void publishQrToTerminal(PaymentRequest payment, String qrImageBase64, String message) {
        if (payment == null || payment.getTerminalId() == null || payment.getTerminalId().trim().isEmpty()) {
            return;
        }

        String enterpriseCode = getEnterpriseCode(payment);

        TerminalQrEvent event = new TerminalQrEvent();
        event.setPaymentId(payment.getPaymentId());
        event.setEnterpriseCode(enterpriseCode);
        event.setTerminalId(payment.getTerminalId());
        event.setTransactionRef(payment.getTransactionRef());
        event.setAmount(payment.getAmount());
        event.setMerchantName(payment.getMerchantName());
        event.setUpiId(payment.getUpiId());
        event.setUpiUrl(payment.getUpiUrl());
        event.setQrImageBase64(qrImageBase64);
        event.setStatus(payment.getStatus());
        event.setMessage(message);
        event.setTimestamp(System.currentTimeMillis());
        event.setSourceApp(payment.getSourceApp());
        messagingTemplate.convertAndSend("/topic/terminal/" + payment.getTerminalId(), event);
    }

    // WebSocket to Web cashier and Mobile terminal: publishes payment status updates.
    public void publishPaymentUpdate(PaymentRequest payment, String message) {
        if (payment == null || payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(payment, message);
        event.setEventType(resolvePaymentUpdateEventType(payment.getStatus()));

        messagingTemplate.convertAndSend("/topic/payment/" + payment.getPaymentId(), event);
        messagingTemplate.convertAndSend("/topic/terminal/" + payment.getTerminalId(), event);
        if (event.getEnterpriseCode() != null && !event.getEnterpriseCode().trim().isEmpty()) {
            messagingTemplate.convertAndSend("/topic/enterprise/" + event.getEnterpriseCode() + "/payments", event);
        }
    }

    // WebSocket to Web cashier: asks the cashier to confirm or reject a PhonePe match.
    public void publishPhonePeConfirmationRequired(PaymentRequest payment, Long notificationId, String payerName) {
        if (payment == null || payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(payment, "PhonePe payment received. Please confirm after checking customer.");
        event.setEventType(PaymentEventType.PHONEPE_PAYMENT_CONFIRMATION_REQUIRED.value());
        event.setNotificationId(notificationId);
        event.setPayerName(payerName);

        messagingTemplate.convertAndSend("/topic/payment/" + payment.getPaymentId(), event);
        messagingTemplate.convertAndSend("/topic/terminal/" + payment.getTerminalId(), event);
    }

    // WebSocket to Web cashiers: sends one PhonePe confirmation prompt per candidate payment.
    public void publishPhonePeConfirmationRequired(List<PaymentRequest> payments, Long notificationId, String payerName) {
        if (payments == null || payments.isEmpty()) {
            return;
        }

        for (PaymentRequest payment : payments) {
            publishPhonePeConfirmationRequired(payment, notificationId, payerName);
        }
    }

    private PaymentStatusEvent buildEvent(PaymentRequest payment, String message) {
        PaymentStatusEvent event = new PaymentStatusEvent();
        event.setPaymentId(payment.getPaymentId());
        event.setEnterpriseCode(getEnterpriseCode(payment));
        event.setTerminalId(payment.getTerminalId());
        event.setStatus(payment.getStatus());
        event.setTransactionRef(payment.getTransactionRef());
        event.setAmount(payment.getAmount());
        event.setPayerName(payment.getPayerName());
        event.setUtr(payment.getUtr());
        event.setMessage(message);
        event.setSourceApp(payment.getSourceApp());
        event.setTimestamp(System.currentTimeMillis());

        return event;
    }

    private String getEnterpriseCode(PaymentRequest payment) {
        try {
            EnterpriseMaster enterprise = payment.getEnterprise();
            return enterprise != null ? enterprise.getEnterpriseCode() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolvePaymentUpdateEventType(String status) {
        if (PaymentStatus.PAID_AUTO_VERIFIED.matches(status)
                || PaymentStatus.PAID_CONFIRMED_BY_CASHIER.matches(status)) {
            return PaymentEventType.PAYMENT_SUCCESS.value();
        }
        if (PaymentStatus.PHONEPE_MATCHED_WAITING_CONFIRMATION.matches(status)) {
            return PaymentEventType.PHONEPE_PAYMENT_CONFIRMATION_REQUIRED.value();
        }
        if (PaymentStatus.WAITING.matches(status)
                || PaymentStatus.EXPIRED.matches(status)
                || PaymentStatus.REJECTED_BY_CASHIER.matches(status)) {
            return PaymentEventType.PAYMENT_STATUS_UPDATED.value();
        }
        return PaymentEventType.PAYMENT_STATUS_UPDATED.value();
    }
}
