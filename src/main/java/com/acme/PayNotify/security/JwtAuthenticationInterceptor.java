/*
 * File: JwtAuthenticationInterceptor.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Requires Bearer JWT authentication on protected PayNotify REST APIs.
 */
package com.acme.PayNotify.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthenticationInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (isPublicRequest(request)) {
            return true;
        }

        String token = extractBearerToken(request.getHeader("Authorization"));
        if (token == null) {
            writeUnauthorized(response, "Missing Authorization Bearer token");
            return false;
        }

        try {
            JwtService.JwtClaims claims = jwtService.validateToken(token);
            request.setAttribute("jwtClaims", claims);
            return true;
        } catch (RuntimeException e) {
            writeUnauthorized(response, e.getMessage());
            return false;
        }
    }

    private boolean isPublicRequest(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        return path.equals("/api/enterprise/validate")
                || path.equals("/api/enterprise/departments")
                || path.equals("/api/enterprise/create")
                || path.equals("/api/device/register")
                || path.equals("/api/device/login")
                || path.startsWith("/ws/");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorizationHeader.startsWith(prefix)) {
            return null;
        }
        return authorizationHeader.substring(prefix.length()).trim();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"" + escape(message) + "\",\"data\":null}");
    }

    private String escape(String value) {
        return value == null ? "Unauthorized" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
