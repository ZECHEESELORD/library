package sh.harold.creative.library.metrics.prometheus;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.metrics.CounterMetric;
import sh.harold.creative.library.metrics.Metrics;
import sh.harold.creative.library.metrics.core.StandardTelemetry;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusHttpExporterTest {

    @Test
    void servesPrometheusScrapesOverHttp() throws Exception {
        CounterMetric metric = Metrics.counter("players_online_total", "Tracks connected players");
        StandardTelemetry telemetry = new StandardTelemetry();
        telemetry.counter(metric).increment();

        try (PrometheusHttpExporter exporter = PrometheusHttpExporter.start(new InetSocketAddress("127.0.0.1", 0), "/metrics", telemetry)) {
            URI uri = exporter.uri();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(uri).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            assertEquals(200, response.statusCode());
            assertEquals(PrometheusFormatter.CONTENT_TYPE, response.headers().firstValue("Content-Type").orElseThrow());
            assertTrue(response.body().contains("players_online_total 1.0"));
        }
    }

    @Test
    void reportsConfiguredAdvertisedUri() throws Exception {
        URI advertisedUri = URI.create("https://metrics.example.test/eternum/prometheus");

        try (StandardTelemetry telemetry = new StandardTelemetry();
             PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
                     new PrometheusHttpExporter.Options(new InetSocketAddress("127.0.0.1", 0), "/metrics", telemetry)
                             .advertisedUri(advertisedUri)
             )) {
            assertEquals(advertisedUri, exporter.uri());
        }
    }

    @Test
    void reportsActualPortAndNormalizedPath() throws Exception {
        try (StandardTelemetry telemetry = new StandardTelemetry();
             PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
                     new PrometheusHttpExporter.Options(new InetSocketAddress("127.0.0.1", 0), "custom-metrics", telemetry)
             )) {
            URI uri = exporter.uri();

            assertEquals("http", uri.getScheme());
            assertEquals("127.0.0.1", uri.getHost());
            assertTrue(uri.getPort() > 0);
            assertEquals("/custom-metrics", uri.getPath());
        }
    }

    @Test
    void reportsLoopbackUriForWildcardBindAddress() throws Exception {
        try (StandardTelemetry telemetry = new StandardTelemetry();
             PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
                     new PrometheusHttpExporter.Options(new InetSocketAddress("0.0.0.0", 0), "/metrics", telemetry)
             )) {
            assertEquals("127.0.0.1", exporter.uri().getHost());
            assertTrue(exporter.uri().getPort() > 0);
        }
    }
}
