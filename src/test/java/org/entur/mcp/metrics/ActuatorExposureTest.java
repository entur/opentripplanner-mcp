package org.entur.mcp.metrics;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Only what the platform needs is exposed on the management port: health for probes and
 * prometheus for scraping. env leaks configuration and loggers lets anyone who reaches the
 * port change log levels at runtime, so they stay off even though the port is internal.
 *
 * <p>Prod reads its config from the Helm configmap, not application.properties, so the
 * exposure list there must match.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0")
class ActuatorExposureTest {

    @LocalManagementPort
    private int managementPort;

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        "health, 200",
        "prometheus, 200",
        "env, 404",
        "loggers, 404",
        "metrics, 404",
        "info, 404",
        "heapdump, 404",
        "threaddump, 404",
    })
    void onlyHealthAndPrometheusAreExposed(String endpoint, int expectedStatus) throws Exception {
        HttpResponse<Void> response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(
                "http://localhost:" + managementPort + "/actuator/" + endpoint)).build(),
            HttpResponse.BodyHandlers.discarding());

        assertThat(response.statusCode()).isEqualTo(expectedStatus);
    }
}
