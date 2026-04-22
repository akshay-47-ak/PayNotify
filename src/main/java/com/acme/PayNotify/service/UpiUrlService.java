package com.acme.PayNotify.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class UpiUrlService {

    public String generateUpiUrl(String upiId, String merchantName, BigDecimal amount,
                                 String transactionRef, String note) {

        StringBuilder sb = new StringBuilder("upi://pay?");
        sb.append("pa=").append(encodeValue(upiId));
        sb.append("&pn=").append(encodeValue(merchantName));
        sb.append("&am=").append(amount != null ? amount.toPlainString() : "0");
        sb.append("&cu=INR");

        if (transactionRef != null && !transactionRef.trim().isEmpty()) {
            sb.append("&tr=").append(encodeValue(transactionRef));
        }

        if (note != null && !note.trim().isEmpty()) {
            sb.append("&tn=").append(encodeValue(note));
        }

        return sb.toString();
    }

    private String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value;
        }
    }
}