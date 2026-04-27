package sh.harold.creative.library.metrics.prometheus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import sh.harold.creative.library.metrics.core.MetricSnapshotSource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class PrometheusHttpExporter implements AutoCloseable {

    private final HttpServer server;
    private final String path;
    private final MetricSnapshotSource source;
    private final PrometheusFormatter formatter;
    private final URI uri;

    private PrometheusHttpExporter(HttpServer server, String path, MetricSnapshotSource source, PrometheusFormatter formatter, URI uri) {
        this.server = server;
        this.path = path;
        this.source = source;
        this.formatter = formatter;
        this.uri = uri;
    }

    public static PrometheusHttpExporter start(InetSocketAddress address, String path, MetricSnapshotSource source) throws IOException {
        return start(address, path, source, null);
    }

    public static PrometheusHttpExporter start(InetSocketAddress address, String path, MetricSnapshotSource source, Executor executor) throws IOException {
        return start(new Options(address, path, source).executor(executor));
    }

    public static PrometheusHttpExporter start(Options options) throws IOException {
        Objects.requireNonNull(options, "options");
        String normalizedPath = normalizePath(options.path);
        HttpServer server = HttpServer.create(options.bindAddress, 0);
        PrometheusFormatter formatter = new PrometheusFormatter();
        server.createContext(normalizedPath, exchange -> handle(exchange, formatter, options.source));
        if (options.executor != null) {
            server.setExecutor(options.executor);
        }
        server.start();
        URI uri = options.advertisedUri == null
                ? deriveUri(options.bindAddress, server.getAddress().getPort(), normalizedPath)
                : options.advertisedUri;
        return new PrometheusHttpExporter(server, normalizedPath, options.source, formatter, uri);
    }

    public URI uri() {
        return uri;
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

    private static URI deriveUri(InetSocketAddress bindAddress, int actualPort, String path) {
        String host = bindAddress.getHostString();
        if (isWildcardHost(host)) {
            host = "127.0.0.1";
        }
        try {
            return new URI("http", null, host, actualPort, path, null, null);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid Prometheus exporter host/path", exception);
        }
    }

    private static boolean isWildcardHost(String host) {
        return host == null
                || host.isBlank()
                || "0.0.0.0".equals(host)
                || "::".equals(host)
                || "0:0:0:0:0:0:0:0".equals(host);
    }

    public static final class Options {

        private final InetSocketAddress bindAddress;
        private final String path;
        private final MetricSnapshotSource source;
        private URI advertisedUri;
        private Executor executor;

        public Options(InetSocketAddress bindAddress, String path, MetricSnapshotSource source) {
            this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
            this.path = Objects.requireNonNull(path, "path");
            this.source = Objects.requireNonNull(source, "source");
        }

        public Options advertisedUri(URI advertisedUri) {
            this.advertisedUri = Objects.requireNonNull(advertisedUri, "advertisedUri");
            return this;
        }

        public Options executor(Executor executor) {
            this.executor = executor;
            return this;
        }
    }
}
