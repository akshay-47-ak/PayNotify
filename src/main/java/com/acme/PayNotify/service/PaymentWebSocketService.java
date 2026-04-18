package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.PaymentStatusEvent;
import com.acme.PayNotify.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebSocketService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void publishPaymentUpdate(PaymentTransaction txn, String message) {
        if (txn == null || txn.getPaymentId() == null || txn.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(txn, message);
        messagingTemplate.convertAndSend("/topic/payment/" + txn.getPaymentId(), event);
    }

    public void publishActivePayment(PaymentTransaction txn, String message) {
        if (txn == null || txn.getPaymentId() == null || txn.getPaymentId().trim().isEmpty()) {
            return;
        }

        PaymentStatusEvent event = buildEvent(txn, message);
        messagingTemplate.convertAndSend("/topic/payment/active", event);
    }

    private PaymentStatusEvent buildEvent(PaymentTransaction txn, String message) {
        PaymentStatusEvent event = new PaymentStatusEvent();
        event.setPaymentId(txn.getPaymentId());
        event.setStatus(txn.getStatus());
        event.setTransactionRef(txn.getTransactionRef());
        event.setAmount(txn.getAmount());
        event.setPayerName(txn.getPayerName());
        event.setUtr(txn.getUtr());
        event.setMessage(message);
        event.setTimestamp(System.currentTimeMillis());
        return event;
    }
}