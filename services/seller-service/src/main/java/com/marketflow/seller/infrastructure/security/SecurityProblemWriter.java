package com.marketflow.seller.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketflow.seller.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.MDC;

final class SecurityProblemWriter {

    private SecurityProblemWriter() {}

    static void write(
            ObjectMapper mapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        mapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "type",
                        "https://marketflow.dev/problems/" + code.toLowerCase(),
                        "title",
                        status == 401 ? "Unauthorized" : "Forbidden",
                        "status",
                        status,
                        "detail",
                        detail,
                        "instance",
                        request.getRequestURI(),
                        "code",
                        code,
                        "correlationId",
                        correlationId == null ? "unknown" : correlationId,
                        "errors",
                        List.of()));
    }
}
