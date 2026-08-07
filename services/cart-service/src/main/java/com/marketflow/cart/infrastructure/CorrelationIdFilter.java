package com.marketflow.cart.infrastructure;

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
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Correlation-ID");
        String id =
                supplied != null && SAFE.matcher(supplied).matches()
                        ? supplied
                        : UUID.randomUUID().toString();
        response.setHeader("X-Correlation-ID", id);
        long started = System.nanoTime();
        try (MDC.MDCCloseable ignored = MDC.putCloseable("correlationId", id)) {
            chain.doFilter(request, response);
        } finally {
            LoggerFactory.getLogger(CorrelationIdFilter.class)
                    .atInfo()
                    .addKeyValue("http.request.method", request.getMethod())
                    .addKeyValue("url.path", request.getRequestURI())
                    .addKeyValue("http.response.status_code", response.getStatus())
                    .addKeyValue("event.duration", System.nanoTime() - started)
                    .log("HTTP request completed");
        }
    }
}
