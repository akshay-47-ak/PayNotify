package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.entity.PaymentTransaction;
import com.acme.PayNotify.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/qr/generate")
    public ResponseEntity<ApiResponse<GenerateQrResponse>> generateQr(@RequestBody GenerateQrRequest request) {
        try {
            GenerateQrResponse response = paymentService.generateQr(request);
            return ResponseEntity.ok(new ApiResponse<GenerateQrResponse>(true, "QR generated successfully", response));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<GenerateQrResponse>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/latest-pending")
    public Map<String, Object> getLatestPending() {
        Map<String, Object> response = new HashMap<>();

        PaymentTransaction txn = paymentService.getLatestPendingPayment();

        response.put("success", txn != null);
        response.put("data", txn);

        return response;
    }

    @GetMapping("/status/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentTransaction>> getStatus(@PathVariable("paymentId") String paymentId) {
        try {
            PaymentTransaction transaction = paymentService.getPaymentStatus(paymentId);
            if (transaction == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ApiResponse<PaymentTransaction>(false, "Payment not found", null));
            }
            return ResponseEntity.ok(new ApiResponse<PaymentTransaction>(true, "Payment status fetched", transaction));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<PaymentTransaction>(false, e.getMessage(), null));
        }
    }

    @PostMapping("/notify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> notifyPayment(@RequestBody PaymentNotificationRequest request) {
        try {
            Map<String, Object> result = paymentService.processNotification(request);
            return ResponseEntity.ok(new ApiResponse<Map<String, Object>>(true, "Notification processed", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse<Map<String, Object>>(false, e.getMessage(), null));
        }
    }

    @GetMapping("/test")
    public String test() {
        return "OK";
    }
}
