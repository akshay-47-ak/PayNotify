package com.acme.PayNotify.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationParserService {

    public Map<String, String> parse(String message) {
        Map<String, String> result = new HashMap<>();

        if (message == null || message.trim().isEmpty()) {
            return result;
        }

        String normalized = message.replace('+', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        String amount = extract(normalized, "(?i)(₹|rs\\.?|inr)\\s*(\\d+(\\.\\d{1,2})?)", 2);
        String utr = extract(normalized, "(?i)(utr|ref no|txn id|transaction id|rrn)[:\\s-]*([A-Za-z0-9\\-]+)", 2);

        String transactionRef = extract(normalized,
                "(?i)\\b([A-Z0-9]+(?:-[A-Z0-9]+)*-TXN[0-9A-Z\\-_]*|TXN[0-9A-Z\\-_]+|PADM-TXN-[A-Z0-9\\-_]+)\\b",
                1);

        if (transactionRef == null) {
            transactionRef = extract(normalized,
                    "(?i)(tr|transaction ref|merchant ref)[:\\s-]*([A-Za-z0-9\\-_]+)",
                    2);
        }

        String payerName = extractPayerName(normalized);

        result.put("amount", amount);
        result.put("utr", utr);
        result.put("transactionRef", transactionRef);
        result.put("payerName", payerName);
        result.put("normalizedMessage", normalized);

        return result;
    }

    private String extractPayerName(String text) {
        String[] patterns = new String[]{
                "(?i)^([A-Za-z][A-Za-z .]{1,80}?)\\s+paid\\s+you\\b",
                "(?i)^([A-Za-z][A-Za-z .]{1,80}?)\\s+sent\\s+you\\b",
                "(?i)received\\s+from\\s+([A-Za-z][A-Za-z .]{1,80}?)(?:\\b|₹|rs|inr)",
                "(?i)from\\s+([A-Za-z][A-Za-z .]{1,80}?)(?:\\b|₹|rs|inr)"
        };

        for (String regex : patterns) {
            String value = extract(text, regex, 1);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }

        return null;
    }

    private String extract(String text, String regex, int group) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(group);
        }
        return null;
    }
}