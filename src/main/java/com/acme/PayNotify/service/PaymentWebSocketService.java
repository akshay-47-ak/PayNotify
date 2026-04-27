package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.PaymentStatusEvent;
import com.acme.PayNotify.dto.TerminalQrEvent;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

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

    public void publishPaymentUpdate(PaymentRequest payment, String message) {
        if (payment == null || payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(payment, message);

        messagingTemplate.convertAndSend("/topic/payment/" + payment.getPaymentId(), event);
        messagingTemplate.convertAndSend("/topic/terminal/" + payment.getTerminalId(), event);
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
}