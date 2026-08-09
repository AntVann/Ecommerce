package com.marketflow.payment.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Correlation-ID");
        String correlation =
                supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,128}")
                        ? supplied
                        : UUID.randomUUID().toString();
        request.setAttribute("correlationId", correlation);
        response.setHeader("X-Correlation-ID", correlation);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", correlation)) {
            chain.doFilter(request, response);
        }
    }
}
