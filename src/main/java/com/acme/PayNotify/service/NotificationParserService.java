package com.acme.PayNotify.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class NotificationParserService {

    public Map<String, String> parse(String message) {

        Map<String, String> result = new HashMap<String, String>();

        if (message == null) return result;

        // Amount extraction
        String amount = extract(message, "(?i)(₹|rs\\.?|inr)\\s*(\\d+(\\.\\d{1,2})?)", 2);

        // UTR extraction
        String utr = extract(message, "(?i)(utr|ref no|txn id|transaction id)[:\\s-]*(\\w+)", 2);

        result.put("amount", amount);
        result.put("utr", utr);

        return result;
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