package com.marketflow.order.infrastructure.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Component
public class CorrelationIdFilter implements Filter {
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest r = (HttpServletRequest) req;
        HttpServletResponse s = (HttpServletResponse) res;
        String id = r.getHeader("X-Correlation-ID");
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,128}")) id = UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        s.setHeader("X-Correlation-ID", id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
