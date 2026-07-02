package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.PaymentStatusEvent;
import com.acme.PayNotify.entity.EnterpriseMaster;
import com.acme.PayNotify.entity.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentWebSocketServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private PaymentWebSocketService paymentWebSocketService;

    @Test
    void waitingPaymentUpdateIsNotPublishedAsSuccess() {
        PaymentRequest payment = payment("WAITING");

        paymentWebSocketService.publishPaymentUpdate(payment, "PhonePe payment handled by another cashier.");

        ArgumentCaptor<PaymentStatusEvent> eventCaptor = ArgumentCaptor.forClass(PaymentStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/payment/PAY-1"), eventCaptor.capture());
        verify(messagingTemplate).convertAndSend(eq("/topic/terminal/TERM-1"), eventCaptor.capture());
        verify(messagingTemplate).convertAndSend(eq("/topic/enterprise/ENT/payments"), eventCaptor.capture());

        for (PaymentStatusEvent event : eventCaptor.getAllValues()) {
            assertEquals("PAYMENT_STATUS_UPDATED", event.getEventType());
            assertEquals("WAITING", event.getStatus());
            assertEquals("PAY-1", event.getPaymentId());
        }
    }

    @Test
    void paidPaymentUpdateIsPublishedAsSuccess() {
        PaymentRequest payment = payment("PAID_CONFIRMED_BY_CASHIER");

        paymentWebSocketService.publishPaymentUpdate(payment, "PhonePe payment confirmed successfully.");

        ArgumentCaptor<PaymentStatusEvent> eventCaptor = ArgumentCaptor.forClass(PaymentStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/payment/PAY-1"), eventCaptor.capture());

        assertEquals("PAYMENT_SUCCESS", eventCaptor.getValue().getEventType());
        assertEquals("PAID_CONFIRMED_BY_CASHIER", eventCaptor.getValue().getStatus());
    }

    @Test
    void phonePeConfirmationRequiredIsOnlyPublishedToMatchedPayment() {
        PaymentRequest payment = payment("PHONEPE_MATCHED_WAITING_CONFIRMATION");

        paymentWebSocketService.publishPhonePeConfirmationRequired(payment, 501L, "Rahul");

        ArgumentCaptor<PaymentStatusEvent> eventCaptor = ArgumentCaptor.forClass(PaymentStatusEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/payment/PAY-1"), eventCaptor.capture());
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/enterprise/ENT/payments"), isA(PaymentStatusEvent.class));

        assertEquals("PHONEPE_PAYMENT_CONFIRMATION_REQUIRED", eventCaptor.getValue().getEventType());
        assertEquals("PHONEPE_MATCHED_WAITING_CONFIRMATION", eventCaptor.getValue().getStatus());
        assertEquals(501L, eventCaptor.getValue().getNotificationId());
    }

    private PaymentRequest payment(String status) {
        EnterpriseMaster enterprise = new EnterpriseMaster();
        enterprise.setEnterpriseCode("ENT");

        PaymentRequest payment = new PaymentRequest();
        payment.setPaymentId("PAY-1");
        payment.setEnterprise(enterprise);
        payment.setTerminalId("TERM-1");
        payment.setStatus(status);
        return payment;
    }
}
