package com.marketflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketflow.sample.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureObservability(metrics = true, tracing = false)
class MarketFlowSampleApplicationIT {

    @LocalServerPort private int port;

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void readinessEndpointIsHealthyAndPreservesSafeCorrelationId() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(CorrelationIdFilter.HEADER_NAME, "m0-readiness-test");

        ResponseEntity<JsonNode> response =
                restTemplate.exchange(
                        url("/actuator/health/readiness"),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
        assertThat(response.getHeaders().getFirst(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo("m0-readiness-test");
    }

    @Test
    void prometheusEndpointPublishesApplicationMetrics() {
        ResponseEntity<String> response =
                restTemplate.getForEntity(url("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_info");
        assertThat(response.getBody()).contains("application=\"marketflow-sample-service\"");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
