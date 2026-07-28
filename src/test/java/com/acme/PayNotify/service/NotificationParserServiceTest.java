/*
 * File: NotificationParserServiceTest.java
 * Created: 2026-06-30
 * Author: Akshay Athavale
 * Use: Contains automated tests for PayNotify behavior.
 */
package com.acme.PayNotify.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotificationParserServiceTest {

    private final NotificationParserService parserService = new NotificationParserService();

    @Test
    void extractsPayerNameFromHasSentBankAccountNotification() {
        Map<String, String> parsed = parserService.parse(
                "SOHAM ANIL SHENDE has sent ₹1 to your bank account Bank Of Maharashtra-4875"
        );

        assertEquals("SOHAM ANIL SHENDE", parsed.get("payerName"));
        assertEquals("1", parsed.get("amount"));
    }
}
