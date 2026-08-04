package com.marketflow.sample.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    @Test
    void preservesSafeClientValue() {
        assertThat(CorrelationIdFilter.resolveCorrelationId("checkout:0192.test-1"))
                .isEqualTo("checkout:0192.test-1");
    }

    @Test
    void replacesMissingBlankUnsafeOrOversizedValues() {
        assertGeneratedUuid(CorrelationIdFilter.resolveCorrelationId(null));
        assertGeneratedUuid(CorrelationIdFilter.resolveCorrelationId("  "));
        assertGeneratedUuid(CorrelationIdFilter.resolveCorrelationId("line-break\r\nvalue"));
        assertGeneratedUuid(CorrelationIdFilter.resolveCorrelationId("x".repeat(129)));
    }

    @Test
    void exposesCorrelationIdToTheResponseAndRequestLogContext()
            throws ServletException, IOException {
        var request = new MockHttpServletRequest("GET", "/actuator/health/readiness");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "m0-smoke-test");
        var response = new MockHttpServletResponse();
        var observedCorrelationId = new AtomicReference<String>();

        new CorrelationIdFilter()
                .doFilter(
                        request,
                        response,
                        (ignoredRequest, ignoredResponse) ->
                                observedCorrelationId.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("m0-smoke-test");
        assertThat(observedCorrelationId).hasValue("m0-smoke-test");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private static void assertGeneratedUuid(String value) {
        assertThat(UUID.fromString(value).toString()).isEqualTo(value);
    }
}
