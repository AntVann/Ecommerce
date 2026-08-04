package com.marketflow.identity.api;

import com.marketflow.identity.infrastructure.security.IdentitySecurityProperties;
import com.marketflow.identity.infrastructure.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public final class ProblemHandler {

    private final IdentitySecurityProperties properties;

    public ProblemHandler(IdentitySecurityProperties properties) {
        this.properties = properties;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> handleValidation(
            Exception exception, HttpServletRequest request) {
        return response(
                HttpStatus.BAD_REQUEST,
                "REQUEST_VALIDATION_400",
                "The request is invalid.",
                request);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("https://marketflow.dev/problems/" + code.toLowerCase()));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        problem.setProperty("errors", List.of());
        var response = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS) {
            response.header(
                    HttpHeaders.RETRY_AFTER,
                    Long.toString(Math.max(1, properties.loginWindow().toSeconds())));
        }
        return response.body(problem);
    }

    private String correlationId(HttpServletRequest request) {
        String fromMdc = MDC.get(CorrelationIdFilter.MDC_KEY);
        return fromMdc == null ? request.getHeader(CorrelationIdFilter.HEADER) : fromMdc;
    }
}
