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
import java.util.List;
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

    public GenerateQrResponse generateQr(GenerateQrRequest request) throws Exception {

        String paymentId = "PAY" + System.currentTimeMillis();

        String transactionRef = request.getTransactionRef();
        if (transactionRef == null || transactionRef.trim().isEmpty()) {
            transactionRef = "TXN" + System.currentTimeMillis();
        }

        String upiUrl = upiUrlService.generateUpiUrl(
                request.getUpiId(),
                request.getMerchantName(),
                request.getAmount(),
                transactionRef,
                request.getNote());

        String qrImageBase64 = qrCodeService.generateQrBase64(upiUrl, 300, 300);

        Timestamp now = new Timestamp(System.currentTimeMillis());

        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setPaymentId(paymentId);
        paymentTransaction.setTransactionRef(transactionRef);
        paymentTransaction.setUpiId(request.getUpiId());
        paymentTransaction.setMerchantName(request.getMerchantName());
        paymentTransaction.setAmount(request.getAmount());
        paymentTransaction.setNote(request.getNote());
        paymentTransaction.setUpiUrl(upiUrl);
        paymentTransaction.setStatus("PENDING");
        paymentTransaction.setCreatedAt(now);
        paymentTransaction.setUpdatedAt(now);

        paymentTransactionRepository.save(paymentTransaction);

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

        if (utr != null && !utr.trim().isEmpty()) {
            Optional<PaymentTransaction> existingByUtr = paymentTransactionRepository.findByUtr(utr.trim());
            if (existingByUtr.isPresent()) {
                PaymentTransaction alreadyMatched = existingByUtr.get();

                response.put("matched", true);
                response.put("status", "DUPLICATE_NOTIFICATION");
                response.put("paymentId", alreadyMatched.getPaymentId());
                response.put("amount", amountStr);
                response.put("utr", utr);
                response.put("payerName", payerName);
                response.put("transactionRef", parsedTransactionRef);
                response.put("fullText", fullText);
                return response;
            }
        }

        PaymentTransaction bestTxn = null;
        int bestScore = -1;

        if (request.getPaymentId() != null && !request.getPaymentId().trim().isEmpty()) {
            Optional<PaymentTransaction> txnOpt = paymentTransactionRepository.findByPaymentId(request.getPaymentId().trim());
            if (txnOpt.isPresent()) {
                PaymentTransaction txn = txnOpt.get();
                int score = calculateMatchScore(txn, receivedAmount, payerName, utr, parsedTransactionRef, fullText);
                bestTxn = txn;
                bestScore = score;
            }
        }

        if ((bestTxn == null || bestScore < 80)
                && parsedTransactionRef != null
                && !parsedTransactionRef.trim().isEmpty()) {

            Optional<PaymentTransaction> txnOpt = paymentTransactionRepository.findByTransactionRef(parsedTransactionRef.trim());
            if (txnOpt.isPresent()) {
                PaymentTransaction txn = txnOpt.get();
                int score = calculateMatchScore(txn, receivedAmount, payerName, utr, parsedTransactionRef, fullText);
                if (score > bestScore) {
                    bestTxn = txn;
                    bestScore = score;
                }
            }
        }

        Timestamp windowStart = new Timestamp(System.currentTimeMillis() - (15 * 60 * 1000));

        if (receivedAmount != null) {
            List<PaymentTransaction> candidates =
                    paymentTransactionRepository.findByStatusAndAmountAndCreatedAtAfter("PENDING", receivedAmount, windowStart);

            for (PaymentTransaction txn : candidates) {
                int score = calculateMatchScore(txn, receivedAmount, payerName, utr, parsedTransactionRef, fullText);
                if (score > bestScore) {
                    bestTxn = txn;
                    bestScore = score;
                }
            }
        } else {
            List<PaymentTransaction> candidates =
                    paymentTransactionRepository.findByStatusAndCreatedAtAfter("PENDING", windowStart);

            for (PaymentTransaction txn : candidates) {
                int score = calculateMatchScore(txn, receivedAmount, payerName, utr, parsedTransactionRef, fullText);
                if (score > bestScore) {
                    bestTxn = txn;
                    bestScore = score;
                }
            }
        }

        boolean matched = bestTxn != null && bestScore >= 40;

        if (matched) {
            if ("SUCCESS".equalsIgnoreCase(bestTxn.getStatus())) {
                response.put("matched", true);
                response.put("status", "ALREADY_SUCCESS");
                response.put("paymentId", bestTxn.getPaymentId());
                response.put("score", bestScore);
                response.put("amount", amountStr);
                response.put("utr", utr);
                response.put("payerName", payerName);
                response.put("transactionRef", parsedTransactionRef);
                response.put("fullText", fullText);
                return response;
            }

            bestTxn.setStatus("SUCCESS");
            bestTxn.setUtr(utr);
            bestTxn.setPayerName(payerName);
            bestTxn.setRawMessage(fullText);
            bestTxn.setUpdatedAt(new Timestamp(System.currentTimeMillis()));

            paymentTransactionRepository.save(bestTxn);
        }

        response.put("matched", matched);
        response.put("status", matched ? "SUCCESS" : "PENDING_REVIEW");
        response.put("paymentId", matched ? bestTxn.getPaymentId() : null);
        response.put("score", bestScore);
        response.put("amount", amountStr);
        response.put("utr", utr);
        response.put("payerName", payerName);
        response.put("transactionRef", parsedTransactionRef);
        response.put("fullText", fullText);

        return response;
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
}