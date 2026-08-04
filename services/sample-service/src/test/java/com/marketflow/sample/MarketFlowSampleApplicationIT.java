package com.marketflow.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.marketflow.sample.infrastructure.web.CorrelationIdFilter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketFlowSampleApplicationIT {

    @LocalServerPort private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void readinessEndpointIsHealthyAndPreservesSafeCorrelationId() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url("/actuator/health/readiness")))
                        .header(CorrelationIdFilter.HEADER_NAME, "m0-readiness-test")
                        .GET()
                        .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
        assertThat(response.headers().firstValue(CorrelationIdFilter.HEADER_NAME).orElseThrow())
                .isEqualTo("m0-readiness-test");
    }

    @Test
    void prometheusEndpointPublishesApplicationMetrics() throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create(url("/actuator/prometheus"))).GET().build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_info");
        assertThat(response.body()).contains("application=\"marketflow-sample-service\"");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
