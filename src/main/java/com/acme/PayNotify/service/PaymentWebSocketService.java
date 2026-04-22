package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.PaymentStatusEvent;
import com.acme.PayNotify.entity.PaymentRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void publishPaymentCreated(PaymentRequest payment, String message) {
        if (payment == null || payment.getPaymentId() == null || payment.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(payment, message);

        messagingTemplate.convertAndSend("/topic/payment/" + payment.getPaymentId(), event);
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
        event.setEnterpriseCode(payment.getEnterprise().getEnterpriseCode());
        event.setTerminalId(payment.getTerminalId());
        event.setStatus(payment.getStatus());
        event.setTransactionRef(payment.getTransactionRef());
        event.setAmount(payment.getAmount());
        event.setPayerName(payment.getPayerName());
        event.setUtr(payment.getUtr());
        event.setMessage(message);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}