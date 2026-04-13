package com.acme.PayNotify.controller;

import com.acme.PayNotify.dto.ApiResponse;
import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.entity.PaymentTransaction;
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
}
