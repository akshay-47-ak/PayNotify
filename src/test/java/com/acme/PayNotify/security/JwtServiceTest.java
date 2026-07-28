/*
 * File: JwtServiceTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated tests for PayNotify JWT creation and validation.
 */
package com.acme.PayNotify.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "UnitTestJwtSecretMustBeLongEnough1234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMinutes", 60L);
    }

    @Test
    void createsAndValidatesJwtToken() {
        JwtService.TokenResponse token = jwtService.createToken("Counter 1", "ENT", "CASHIER", "MOBILE");

        JwtService.JwtClaims claims = jwtService.validateToken(token.token());

        assertNotNull(token.token());
        assertEquals("Counter 1", claims.subject());
        assertEquals("ENT", claims.enterpriseCode());
        assertEquals("CASHIER", claims.role());
        assertEquals("MOBILE", claims.clientType());
        assertEquals(token.expiresAt(), claims.expiresAt());
    }

    @Test
    void rejectsTamperedJwtToken() {
        JwtService.TokenResponse token = jwtService.createToken("Counter 1", "ENT", "CASHIER", "MOBILE");
        String tamperedToken = token.token().substring(0, token.token().length() - 2) + "xx";

        RuntimeException exception = assertThrows(RuntimeException.class, () -> jwtService.validateToken(tamperedToken));

        assertEquals("Invalid JWT signature", exception.getMessage());
    }

    @Test
    void rejectsExpiredJwtToken() {
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMinutes", -1L);
        JwtService.TokenResponse token = jwtService.createToken("Counter 1", "ENT", "CASHIER", "MOBILE");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> jwtService.validateToken(token.token()));

        assertEquals("JWT token is expired", exception.getMessage());
    }
}
