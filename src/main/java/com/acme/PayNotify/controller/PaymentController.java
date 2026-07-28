/*
 * File: PaymentController.java
 * Created: 2026-04-13
 * Author: Akshay Athavale
 * Use: Defines REST API endpoints and marks whether calls are for the Web cashier app or Mobile app.
 */
package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PaymentStatusResponse;
import com.acme.PayNotify.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // Web cashier API: creates a QR payment request for Google Pay or PhonePe.
    @PostMapping("/qr/generate")
    public ResponseEntity<ApiResponse<GenerateQrResponse>> generateQr(
            @RequestBody GenerateQrRequest request) {
        try {
            GenerateQrResponse response = paymentService.generateQr(request);
            return ResponseEntity.ok(
                    new ApiResponse<>(true, "QR generated successfully", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Web cashier API: resumes the latest active payment for a selected terminal.
    @GetMapping("/latest-pending")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getLatestPending(
            @RequestParam("enterpriseCode") String enterpriseCode,
            @RequestParam("terminalId") String terminalId) {
        try {
            PaymentStatusResponse response =
                    paymentService.getLatestPendingPayment(enterpriseCode, terminalId);

            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(false, "No pending payment found", null));
            }

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Latest pending payment fetched", response)
            );
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Web cashier API: polling fallback when WebSocket status updates are unavailable.
    @GetMapping("/status/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentStatusResponse>> getStatus(
            @PathVariable("paymentId") String paymentId) {
        try {
            PaymentStatusResponse response = paymentService.getPaymentStatus(paymentId);

            if (response == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<>(false, "Payment not found", null));
            }

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Payment status fetched", response)
            );
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<>(false, e.getMessage(), null));
        }
    }

    // Mobile app API: Android notification listener posts UPI payment notifications here.
    @PostMapping("/notify")
    public ResponseEntity<ApiResponse<PaymentNotificationResponse>> notifyPayment(
            @RequestBody PaymentNotificationRequest request) {
        try {
            PaymentNotificationResponse response = paymentService.processNotification(request);

            return ResponseEntity.ok(
                    new ApiResponse<>(true, "Notification processed", response)
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
