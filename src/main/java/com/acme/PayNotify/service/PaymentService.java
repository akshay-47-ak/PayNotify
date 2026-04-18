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

        String transactionRef = request.getTransactionRef();
        if (transactionRef == null || transactionRef.trim().isEmpty()) {
            transactionRef = "TXN" + System.currentTimeMillis();
        }

        String finalNote = request.getNote();
        if (finalNote == null || finalNote.trim().isEmpty()) {
            finalNote = "PADM PAY " + transactionRef;
        } else if (!finalNote.toUpperCase().contains(transactionRef.toUpperCase())) {
            finalNote = finalNote + " " + transactionRef;
        }

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

        System.out.println("Notify paymentId: " + request.getPaymentId());
        System.out.println("Notify packageName: " + request.getPackageName());
        System.out.println("Notify title: " + request.getTitle());
        System.out.println("Notify message: " + request.getMessage());
        System.out.println("Notify fullText: " + fullText);

        if (request.getPaymentId() == null || request.getPaymentId().trim().isEmpty()) {
            response.put("matched", false);
            response.put("status", "PAYMENT_ID_REQUIRED");
            response.put("fullText", fullText);
            return response;
        }

        PaymentTransaction txn = paymentTransactionRepository
                .findByPaymentId(request.getPaymentId().trim())
                .orElse(null);

        if (txn == null) {
            response.put("matched", false);
            response.put("status", "NOT_FOUND");
            response.put("fullText", fullText);
            return response;
        }

        if ("SUCCESS".equalsIgnoreCase(txn.getStatus())) {
            response.put("matched", true);
            response.put("status", "ALREADY_SUCCESS");
            response.put("paymentId", txn.getPaymentId());
            response.put("fullText", fullText);
            return response;
        }

        Map<String, String> parsed = notificationParserService.parse(fullText);

        String amountStr = parsed.get("amount");
        String utr = parsed.get("utr");
        String payerName = parsed.get("payerName");
        String parsedTransactionRef = parsed.get("transactionRef");

        BigDecimal receivedAmount = null;
        if (amountStr != null && !amountStr.trim().isEmpty()) {
            try {
                receivedAmount = new BigDecimal(amountStr);
            } catch (Exception e) {
                System.out.println("Amount parse failed: " + amountStr);
            }
        }

        boolean amountMatched = false;
        boolean refMatched = false;

        if (receivedAmount != null && txn.getAmount() != null
                && txn.getAmount().compareTo(receivedAmount) == 0) {
            amountMatched = true;
        }

        String normalizedFullText = normalizeText(fullText);
        String normalizedTxnRef = normalizeText(txn.getTransactionRef());
        String normalizedNote = normalizeText(txn.getNote());

        if (normalizedTxnRef != null && !normalizedTxnRef.isEmpty()
                && normalizedFullText.contains(normalizedTxnRef)) {
            refMatched = true;
        }

        if (!refMatched && normalizedNote != null && !normalizedNote.isEmpty()
                && normalizedFullText.contains(normalizedNote)) {
            refMatched = true;
        }

        boolean matched = amountMatched || refMatched;

        if (matched) {
            txn.setStatus("SUCCESS");
            txn.setUtr(utr);
            txn.setPayerName(payerName);
            txn.setRawMessage(fullText);
            txn.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

            txn = paymentTransactionRepository.save(txn);

            paymentWebSocketService.publishPaymentUpdate(txn, "Payment received successfully");
        }

        response.put("matched", matched);
        response.put("status", matched ? "SUCCESS" : "PENDING_REVIEW");
        response.put("paymentId", txn.getPaymentId());
        response.put("transactionRef", txn.getTransactionRef());
        response.put("amount", amountStr);
        response.put("utr", utr);
        response.put("payerName", payerName);
        response.put("fullText", fullText);
        response.put("parsedTransactionRef", parsedTransactionRef);

        return response;
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