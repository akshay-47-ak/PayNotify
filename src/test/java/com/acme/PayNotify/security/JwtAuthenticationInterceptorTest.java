/*
 * File: JwtAuthenticationInterceptorTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated tests for PayNotify REST JWT authorization.
 */
package com.acme.PayNotify.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationInterceptorTest {

    private JwtService jwtService;
    private JwtAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "UnitTestJwtSecretMustBeLongEnough1234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMinutes", 60L);

        interceptor = new JwtAuthenticationInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtService", jwtService);
    }

    @Test
    void allowsPublicEnterpriseValidationWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/enterprise/validate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
    }

    @Test
    void rejectsProtectedApiWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment/qr/generate");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertEquals(false, allowed);
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Missing Authorization Bearer token"));
    }

    @Test
    void allowsProtectedApiWithValidBearerToken() throws Exception {
        JwtService.TokenResponse token = jwtService.createToken("ENT", "ENT", "WEB_CASHIER", "WEB");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment/qr/generate");
        request.addHeader("Authorization", "Bearer " + token.token());
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void rejectsProtectedApiWithInvalidBearerToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/payment/qr/generate");
        request.addHeader("Authorization", "Bearer invalid.token.value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertEquals(false, allowed);
        assertEquals(401, response.getStatus());
    }
}
