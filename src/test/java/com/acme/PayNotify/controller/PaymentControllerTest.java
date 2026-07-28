/*
 * File: PaymentControllerTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated API tests for PayNotify payment endpoints.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PaymentStatusResponse;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PaymentController controller = new PaymentController();
        ReflectionTestUtils.setField(controller, "paymentService", paymentService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void generateQrApiReturnsQrForWebCashier() throws Exception {
        GenerateQrResponse response = new GenerateQrResponse(
                "PAY-1",
                "PADM-TXN-100",
                "TERM-1",
                "upi://pay",
                "data:image/png;base64,qr",
                "WAITING",
                "GOOGLE_PAY",
                123L
        );
        when(paymentService.generateQr(any())).thenReturn(response);

        mockMvc.perform(post("/api/payment/qr/generate")
                        .contentType("application/json")
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value("PAY-1"))
                .andExpect(jsonPath("$.data.status").value("WAITING"))
                .andExpect(jsonPath("$.data.sourceApp").value("GOOGLE_PAY"));
    }

    @Test
    void latestPendingApiReturnsNotFoundWhenNoPaymentExists() throws Exception {
        when(paymentService.getLatestPendingPayment("ENT", "TERM-1")).thenReturn(null);

        mockMvc.perform(get("/api/payment/latest-pending")
                        .param("enterpriseCode", "ENT")
                        .param("terminalId", "TERM-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("No pending payment found"));
    }

    @Test
    void paymentStatusApiReturnsStatusForWebPolling() throws Exception {
        PaymentStatusResponse response = new PaymentStatusResponse();
        response.setPaymentId("PAY-1");
        response.setEnterpriseCode("ENT");
        response.setTerminalId("TERM-1");
        response.setAmount(new BigDecimal("500.00"));
        response.setStatus("WAITING");
        when(paymentService.getPaymentStatus("PAY-1")).thenReturn(response);

        mockMvc.perform(get("/api/payment/status/PAY-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.paymentId").value("PAY-1"))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    void paymentStatusApiReturnsNotFoundForUnknownPayment() throws Exception {
        when(paymentService.getPaymentStatus("PAY-404")).thenReturn(null);

        mockMvc.perform(get("/api/payment/status/PAY-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Payment not found"));
    }

    @Test
    void notifyApiProcessesMobileNotification() throws Exception {
        PaymentNotificationResponse response = new PaymentNotificationResponse();
        response.setMatched(true);
        response.setStatus("PAID_AUTO_VERIFIED");
        response.setPaymentId("PAY-1");
        when(paymentService.processNotification(any())).thenReturn(response);

        mockMvc.perform(post("/api/payment/notify")
                        .contentType("application/json")
                        .content("{\"enterpriseCode\":\"ENT\",\"deviceIdentifier\":\"DEVICE-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PAID_AUTO_VERIFIED"));
    }

    private String requestJson() {
        return "{"
                + "\"enterpriseCode\":\"ENT\","
                + "\"terminalId\":\"TERM-1\","
                + "\"merchantName\":\"Merchant\","
                + "\"upiId\":\"merchant@upi\","
                + "\"amount\":500.00,"
                + "\"sourceApp\":\"GOOGLE_PAY\","
                + "\"documentOwnCode\":123"
                + "}";
    }
}
