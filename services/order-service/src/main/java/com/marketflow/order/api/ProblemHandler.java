package com.marketflow.order.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> api(ApiException e, HttpServletRequest r) {
        return response(e.status(), e.code(), e.getMessage(), r);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    ResponseEntity<ProblemDetail> validation(Exception e, HttpServletRequest r) {
        return response(
                HttpStatus.BAD_REQUEST, "REQUEST_VALIDATION_400", "The request is invalid.", r);
    }

    private ResponseEntity<ProblemDetail> response(
            HttpStatus s, String code, String detail, HttpServletRequest r) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(s, detail);
        p.setTitle(s.getReasonPhrase());
        p.setType(URI.create("https://marketflow.dev/problems/" + code.toLowerCase()));
        p.setInstance(URI.create(r.getRequestURI()));
        p.setProperty("code", code);
        p.setProperty(
                "correlationId",
                MDC.get("correlationId") == null ? "unknown" : MDC.get("correlationId"));
        p.setProperty("errors", List.of());
        return ResponseEntity.status(s).body(p);
    }
}
