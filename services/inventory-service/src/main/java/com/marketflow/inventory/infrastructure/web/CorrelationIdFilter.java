package com.marketflow.inventory.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String supplied = req.getHeader("X-Correlation-ID");
        String id =
                supplied != null && SAFE.matcher(supplied).matches()
                        ? supplied
                        : UUID.randomUUID().toString();
        res.setHeader("X-Correlation-ID", id);
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", id)) {
            chain.doFilter(req, res);
        } finally {
            LoggerFactory.getLogger(CorrelationIdFilter.class)
                    .atInfo()
                    .addKeyValue("http.request.method", req.getMethod())
                    .addKeyValue("url.path", req.getRequestURI())
                    .addKeyValue("http.response.status_code", res.getStatus())
                    .addKeyValue("event.duration", System.nanoTime() - started)
                    .log("HTTP request completed");
        }
    }
}
