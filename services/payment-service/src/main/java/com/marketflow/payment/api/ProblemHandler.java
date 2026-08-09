package com.marketflow.payment.api;

import com.marketflow.payment.application.PaymentService.PaymentException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(PaymentException.class)
    ProblemDetail payment(PaymentException exception, HttpServletRequest request) {
        HttpStatus status =
                exception.getMessage().contains("NOT_FOUND")
                        ? HttpStatus.NOT_FOUND
                        : HttpStatus.CONFLICT;
        return problem(status, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail detail =
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "PAYMENT_REQUEST_INVALID", request);
        detail.setProperty(
                "errors",
                exception.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        Map.of(
                                                "field",
                                                error.getField(),
                                                "message",
                                                error.getDefaultMessage()))
                        .toList());
        return detail;
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, code);
        detail.setTitle("Payment request failed");
        detail.setType(
                URI.create(
                        "https://marketflow.dev/errors/" + code.toLowerCase().replace('_', '-')));
        detail.setProperty("code", code);
        detail.setProperty("correlationId", request.getAttribute("correlationId"));
        return detail;
    }
}
