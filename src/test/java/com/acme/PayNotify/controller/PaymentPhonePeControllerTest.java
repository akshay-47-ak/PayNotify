/*
 * File: PaymentPhonePeControllerTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated API tests for PayNotify cashier payment action endpoints.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentPhonePeControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentPhonePeController controller = new PaymentPhonePeController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void confirmPhonePeApiReturnsSuccess() throws Exception {
        when(paymentService.confirmPhonePePayment(eq("PAY-1"), any())).thenReturn(response(
                true,
                "PAID_CONFIRMED_BY_CASHIER",
                "PhonePe payment confirmed successfully."
        ));

        mockMvc.perform(post("/api/payments/PAY-1/phonepe/confirm")
                        .contentType("application/json")
                        .content("{\"notificationId\":501}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID_CONFIRMED_BY_CASHIER"));
    }

    @Test
    void rejectPhonePeApiReturnsRejectedResponse() throws Exception {
        when(paymentService.rejectPhonePePayment(eq("PAY-1"), any())).thenReturn(response(
                false,
                "REJECTED_BY_CASHIER",
                "PhonePe payment rejected for this payment request."
        ));

        mockMvc.perform(post("/api/payments/PAY-1/phonepe/reject")
                        .contentType("application/json")
                        .content("{\"notificationId\":501,\"reason\":\"Not my customer\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED_BY_CASHIER"));
    }

    @Test
    void manualConfirmApiWorksForGooglePayAndPhonePeFallback() throws Exception {
        when(paymentService.manuallyConfirmPayment(eq("PAY-1"), any())).thenReturn(response(
                true,
                "PAID_CONFIRMED_BY_CASHIER",
                "Payment manually confirmed by cashier."
        ));

        mockMvc.perform(post("/api/payments/PAY-1/manual-confirm")
                        .contentType("application/json")
                        .content("{\"utr\":\"UTR-1\",\"payerName\":\"Rahul\",\"reason\":\"Verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID_CONFIRMED_BY_CASHIER"));
    }

    @Test
    void phonePeManualConfirmAliasUsesGenericManualConfirmFlow() throws Exception {
        when(paymentService.manuallyConfirmPayment(eq("PAY-1"), any())).thenReturn(response(
                true,
                "PAID_CONFIRMED_BY_CASHIER",
                "Payment manually confirmed by cashier."
        ));

        mockMvc.perform(post("/api/payments/PAY-1/phonepe/manual-confirm")
                        .contentType("application/json")
                        .content("{\"reason\":\"Verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID_CONFIRMED_BY_CASHIER"));
    }

    @Test
    void cancelApiReturnsCancelledStatusForCashFlowSwitch() throws Exception {
        when(paymentService.cancelOnlinePayment(eq("PAY-1"), any())).thenReturn(response(
                false,
                "CANCELLED_BY_CASHIER",
                "Online payment cancelled by cashier. Collect cash payment."
        ));

        mockMvc.perform(post("/api/payments/PAY-1/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"Customer will pay cash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED_BY_CASHIER"));
    }

    @Test
    void actionApiReturnsBadRequestForBusinessError() throws Exception {
        when(paymentService.cancelOnlinePayment(eq("PAY-1"), any()))
                .thenThrow(new RuntimeException("Payment is not active and cannot be cancelled"));

        mockMvc.perform(post("/api/payments/PAY-1/cancel")
                        .contentType("application/json")
                        .content("{\"reason\":\"Customer will pay cash\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment is not active and cannot be cancelled"));
    }

    private PaymentNotificationResponse response(boolean matched, String status, String message) {
        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setMatched(matched);
        response.setStatus(status);
        response.setPaymentId("PAY-1");
        response.setTransactionRef("PADM-TXN-100");
        response.setExpectedAmount(new BigDecimal("500.00"));
        response.setMessage(message);
        return response;
    }
}
