/*
 * File: JwtHandshakeInterceptorTest.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Contains automated tests for PayNotify WebSocket JWT authorization.
 */
package com.acme.PayNotify.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtHandshakeInterceptorTest {

    private JwtService jwtService;
    private JwtHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "UnitTestJwtSecretMustBeLongEnough1234567890");
        ReflectionTestUtils.setField(jwtService, "jwtExpiryMinutes", 60L);

        interceptor = new JwtHandshakeInterceptor();
        ReflectionTestUtils.setField(interceptor, "jwtService", jwtService);
    }

    @Test
    void rejectsWebSocketHandshakeWithoutToken() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("/ws/websocket", null, null),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertEquals(false, allowed);
        assertEquals(401, servletResponse.getStatus());
    }

    @Test
    void allowsWebSocketHandshakeWithQueryTokenForSockJsClients() {
        JwtService.TokenResponse token = jwtService.createToken("ENT", "ENT", "WEB_CASHIER", "WEB");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("/ws/websocket", "token=" + token.token(), null),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertTrue(allowed);
        assertEquals(200, servletResponse.getStatus());
    }

    @Test
    void allowsWebSocketHandshakeWithAuthorizationHeader() {
        JwtService.TokenResponse token = jwtService.createToken("Counter 1", "ENT", "CASHIER", "MOBILE");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("/ws/websocket", null, "Bearer " + token.token()),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertTrue(allowed);
        assertEquals(200, servletResponse.getStatus());
    }

    @Test
    void allowsSockJsInfoProbeWithoutToken() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        boolean allowed = interceptor.beforeHandshake(
                request("/ws/info", null, null),
                new ServletServerHttpResponse(servletResponse),
                null,
                new HashMap<>()
        );

        assertTrue(allowed);
    }

    private ServletServerHttpRequest request(String uri, String queryString, String authorizationHeader) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", uri);
        if (queryString != null) {
            servletRequest.setQueryString(queryString);
        }
        if (authorizationHeader != null) {
            servletRequest.addHeader("Authorization", authorizationHeader);
        }
        return new ServletServerHttpRequest(servletRequest);
    }
}
