package com.marketflow.cart.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class GuestCsrfFilter extends OncePerRequestFilter {
    private static final String GUEST_CART_COOKIE = "MARKETFLOW_GUEST_CART";
    public static final String COOKIE = "MARKETFLOW_GUEST_CSRF";
    public static final String HEADER = "X-CSRF-Token";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/cart")
                || request.getMethod().matches("GET|HEAD|OPTIONS")
                || SecurityContextHolder.getContext().getAuthentication() != null;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cookie = cookie(request, COOKIE);
        String header = request.getHeader(HEADER);
        String guestCart = cookie(request, GUEST_CART_COOKIE);
        if (guestCart == null
                || cookie == null
                || header == null
                || !MessageDigest.isEqual(
                        cookie.getBytes(StandardCharsets.UTF_8),
                        header.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter()
                    .write(
                            "{\"title\":\"Forbidden\",\"status\":403,\"code\":\"GUEST_CSRF_INVALID_403\",\"detail\":\"Guest CSRF validation failed.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> c.getName().equals(name))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
