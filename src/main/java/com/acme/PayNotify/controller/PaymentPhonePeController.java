package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.PaymentNotificationResponse;
import com.acme.PayNotify.dto.PhonePeConfirmRequest;
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
}
