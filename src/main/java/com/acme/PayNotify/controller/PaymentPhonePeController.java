/*
 * File: PaymentPhonePeController.java
 * Created: 2026-06-30
 * Author: Akshay Athavale
 * Use: Defines REST API endpoints and marks whether calls are for the Web cashier app or Mobile app.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.CancelPaymentRequest;
import com.acme.PayNotify.dto.ManualPaymentConfirmRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PhonePeConfirmRequest;
import com.acme.PayNotify.dto.PhonePeManualConfirmRequest;
import com.acme.PayNotify.dto.PhonePeRejectRequest;
import com.acme.PayNotify.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@CrossOrigin(origins = "*")
public class PaymentPhonePeController {

    @Autowired
    private PaymentService paymentService;

    // Web cashier API: confirms a PhonePe notification matched to this payment.
    @PostMapping("/{paymentAttemptId}/phonepe/confirm")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> confirmPhonePePayment(
            @PathVariable("paymentAttemptId") String paymentAttemptId,
            @RequestBody PhonePeConfirmRequest request) {
        try {
            PaymentNotificationResponse response =
                    paymentService.confirmPhonePePayment(paymentAttemptId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "PhonePe payment confirmed successfully.", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Web cashier API: rejects a PhonePe notification that does not belong to this payment.
    @PostMapping("/{paymentAttemptId}/phonepe/reject")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> rejectPhonePePayment(
            @PathVariable("paymentAttemptId") String paymentAttemptId,
            @RequestBody PhonePeRejectRequest request) {
        try {
            PaymentNotificationResponse response =
                    paymentService.rejectPhonePePayment(paymentAttemptId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "PhonePe payment rejected by cashier.", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Web cashier API: legacy PhonePe-specific manual confirmation route.
    @PostMapping("/{paymentAttemptId}/phonepe/manual-confirm")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> manuallyConfirmPhonePePayment(
            @PathVariable("paymentAttemptId") String paymentAttemptId,
            @RequestBody(required = false) PhonePeManualConfirmRequest request) {
        return manuallyConfirmPayment(paymentAttemptId, request);
    }

    // Web cashier API: manually confirms Google Pay or PhonePe after the fallback window.
    @PostMapping("/{paymentAttemptId}/manual-confirm")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> manuallyConfirmPayment(
            @PathVariable("paymentAttemptId") String paymentAttemptId,
            @RequestBody(required = false) ManualPaymentConfirmRequest request) {
        try {
            PaymentNotificationResponse response =
                    paymentService.manuallyConfirmPayment(paymentAttemptId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Payment manually confirmed successfully.", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Web cashier API: cancels an active online payment so the customer can pay cash.
    @PostMapping("/{paymentAttemptId}/cancel")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> cancelPayment(
            @PathVariable("paymentAttemptId") String paymentAttemptId,
            @RequestBody(required = false) CancelPaymentRequest request) {
        try {
            PaymentNotificationResponse response =
                    paymentService.cancelOnlinePayment(paymentAttemptId, request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Online payment cancelled by cashier.", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }
}
