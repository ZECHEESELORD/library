package sh.harold.creative.library.metrics.prometheus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sh.harold.creative.library.metrics.core.MetricSnapshotSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class PrometheusHttpExporter implements AutoCloseable {

    private final HttpServer server;
    private final String path;
    private final MetricSnapshotSource source;
    private final PrometheusFormatter formatter;

    private PrometheusHttpExporter(HttpServer server, String path, MetricSnapshotSource source, PrometheusFormatter formatter) {
        this.server = server;
        this.path = path;
        this.source = source;
        this.formatter = formatter;
    }

    public static PrometheusHttpExporter start(InetSocketAddress address, String path, MetricSnapshotSource source) throws IOException {
        return start(address, path, source, null);
    }

    public static PrometheusHttpExporter start(InetSocketAddress address, String path, MetricSnapshotSource source, Executor executor) throws IOException {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(source, "source");
        String normalizedPath = normalizePath(path);
        HttpServer server = HttpServer.create(address, 0);
        PrometheusFormatter formatter = new PrometheusFormatter();
        server.createContext(normalizedPath, exchange -> handle(exchange, formatter, source));
        if (executor != null) {
            server.setExecutor(executor);
        }
        server.start();
        return new PrometheusHttpExporter(server, normalizedPath, source, formatter);
    }

    public URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    public String scrape() {
        return formatter.format(source);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange, PrometheusFormatter formatter, MetricSnapshotSource source) throws IOException {
        byte[] payload = formatter.format(source).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", PrometheusFormatter.CONTENT_TYPE);
        exchange.sendResponseHeaders(200, payload.length);
        try (exchange) {
            exchange.getResponseBody().write(payload);
        }
    }

    private static String normalizePath(String path) {
        Objects.requireNonNull(path, "path");
        if (path.isBlank()) {
            return "/metrics";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
