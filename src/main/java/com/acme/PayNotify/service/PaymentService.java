package com.acme.PayNotify.service;

import com.acme.PayNotify.dto.GenerateQrRequest;
import com.acme.PayNotify.dto.GenerateQrResponse;
import com.acme.PayNotify.dto.PaymentNotificationRequest;
import com.acme.PayNotify.entity.PaymentTransaction;
import com.acme.PayNotify.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private UpiUrlService upiUrlService;

    @Autowired
    private QrCodeService qrCodeService;

    @Autowired
    private NotificationParserService notificationParserService;

    @Autowired
    private PaymentWebSocketService paymentWebSocketService;

    public GenerateQrResponse generateQr(GenerateQrRequest request) throws Exception {

        String paymentId = "PAY" + System.currentTimeMillis();

        // Always generate unique transactionRef at backend
        String transactionRef = "PADM-TXN-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        String finalNote = transactionRef;

        String upiUrl = upiUrlService.generateUpiUrl(
                request.getUpiId(),
                request.getMerchantName(),
                request.getAmount(),
                transactionRef,
                finalNote
        );

        String qrImageBase64 = qrCodeService.generateQrBase64(upiUrl, 300, 300);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setPaymentId(paymentId);
        paymentTransaction.setTransactionRef(transactionRef);
        paymentTransaction.setUpiId(request.getUpiId());
        paymentTransaction.setMerchantName(request.getMerchantName());
        paymentTransaction.setAmount(request.getAmount());
        paymentTransaction.setNote(finalNote);
        paymentTransaction.setUpiUrl(upiUrl);
        paymentTransaction.setStatus("PENDING");
        paymentTransaction.setCreatedAt(now);
        paymentTransaction.setUpdatedAt(now);

        paymentTransaction = paymentTransactionRepository.save(paymentTransaction);
        paymentWebSocketService.publishActivePayment(paymentTransaction, "New payment created");

        GenerateQrResponse response = new GenerateQrResponse();
        response.setPaymentId(paymentId);
        response.setTransactionRef(transactionRef);
        response.setUpiUrl(upiUrl);
        response.setQrImageBase64(qrImageBase64);

        return response;
    }

    public PaymentTransaction getPaymentStatus(String paymentId) {
        Optional<PaymentTransaction> optional = paymentTransactionRepository.findByPaymentId(paymentId);
        return optional.orElse(null);
    }

    public Map<String, Object> processNotification(PaymentNotificationRequest request) {

        Map<String, Object> response = new HashMap<String, Object>();

        String fullText = ((request.getTitle() != null ? request.getTitle() : "") + " "
                + (request.getMessage() != null ? request.getMessage() : "")).trim();

        System.out.println("Notify packageName: " + request.getPackageName());
        System.out.println("Notify title: " + request.getTitle());
        System.out.println("Notify message: " + request.getMessage());
        System.out.println("Notify fullText: " + fullText);

        Map<String, String> parsed = notificationParserService.parse(fullText);

        String amountStr = parsed.get("amount");
        String utr = parsed.get("utr");
        String payerName = parsed.get("payerName");
        String parsedTransactionRef = parsed.get("transactionRef");

        String requestTransactionRef = request.getTransactionRef();

        String finalTransactionRef = firstNonBlank(requestTransactionRef, parsedTransactionRef);

        response.put("parsedTransactionRef", parsedTransactionRef);
        response.put("requestTransactionRef", requestTransactionRef);
        response.put("finalTransactionRef", finalTransactionRef);
        response.put("fullText", fullText);

        if (finalTransactionRef == null || finalTransactionRef.trim().isEmpty()) {
            response.put("matched", false);
            response.put("status", "TRANSACTION_REF_NOT_FOUND");
            return response;
        }

        PaymentTransaction txn = paymentTransactionRepository
                .findTopByTransactionRefAndStatusOrderByCreatedAtDesc(finalTransactionRef.trim(), "PENDING")
                .orElse(null);

        if (txn == null) {
            response.put("matched", false);
            response.put("status", "PENDING_PAYMENT_NOT_FOUND");
            response.put("transactionRef", finalTransactionRef);
            return response;
        }

        if ("SUCCESS".equalsIgnoreCase(txn.getStatus())) {
            response.put("matched", true);
            response.put("status", "ALREADY_SUCCESS");
            response.put("paymentId", txn.getPaymentId());
            response.put("transactionRef", txn.getTransactionRef());
            return response;
        }

        BigDecimal receivedAmount = null;
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                receivedAmount = new BigDecimal(amountStr);
            } catch (Exception e) {
                System.out.println("Amount parse failed: " + amountStr);
            }
        }

        boolean amountMatched = false;
        if (receivedAmount != null && txn.getAmount() != null
                && txn.getAmount().compareTo(receivedAmount) == 0) {
            amountMatched = true;
        }

        // Main matching is txnRef, amount is additional validation
        boolean matched = true;

        txn.setStatus("SUCCESS");
        txn.setUtr(utr);
        txn.setPayerName(payerName);
        txn.setRawMessage(fullText);
        txn.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

        txn = paymentTransactionRepository.save(txn);

        paymentWebSocketService.publishPaymentUpdate(txn, "Payment received successfully");

        response.put("matched", matched);
        response.put("status", "SUCCESS");
        response.put("paymentId", txn.getPaymentId());
        response.put("transactionRef", txn.getTransactionRef());
        response.put("expectedAmount", txn.getAmount());
        response.put("receivedAmount", amountStr);
        response.put("amountMatched", amountMatched);
        response.put("utr", utr);
        response.put("payerName", payerName);

        return response;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }

        return text.replace('+', ' ')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }

    private int calculateMatchScore(
            PaymentTransaction txn,
            BigDecimal receivedAmount,
            String payerName,
            String utr,
            String parsedTransactionRef,
            String fullText) {

        if (txn == null) {
            return -1;
        }

        if (txn.getStatus() != null && !"PENDING".equalsIgnoreCase(txn.getStatus())) {
            return -1;
        }

        int score = 0;

        if (receivedAmount != null
                && txn.getAmount() != null
                && txn.getAmount().compareTo(receivedAmount) == 0) {
            score += 40;
        }

        if (parsedTransactionRef != null
                && txn.getTransactionRef() != null
                && parsedTransactionRef.equalsIgnoreCase(txn.getTransactionRef())) {
            score += 80;
        }

        if (txn.getTransactionRef() != null
                && fullText != null
                && fullText.toLowerCase().contains(txn.getTransactionRef().toLowerCase())) {
            score += 80;
        }

        if (txn.getNote() != null
                && fullText != null
                && fullText.toLowerCase().contains(txn.getNote().toLowerCase())) {
            score += 20;
        }

        if (payerName != null
                && txn.getPayerName() != null
                && payerName.equalsIgnoreCase(txn.getPayerName())) {
            score += 20;
        }

        if (utr != null
                && txn.getUtr() != null
                && utr.equalsIgnoreCase(txn.getUtr())) {
            score += 100;
        }

        if (txn.getCreatedAt() != null) {
            long ageMillis = System.currentTimeMillis() - txn.getCreatedAt().getTime();
            if (ageMillis <= 5 * 60 * 1000) {
                score += 20;
            } else if (ageMillis <= 15 * 60 * 1000) {
                score += 10;
            }
        }

        return score;
    }

    public PaymentTransaction getLatestPendingPayment() {
        return paymentTransactionRepository
                .findTopByStatusOrderByCreatedAtDesc("PENDING")
                .orElse(null);
    }
}