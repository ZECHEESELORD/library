package sh.harold.library.telemetry;

import java.util.List;

/**
 * Where rows go. A sink receives the header once, then rows in order. The CSV sink in {@code telemetry-core}
 * writes {@code tick,partial,<columns...>}.
 */
public interface TelemetrySink extends AutoCloseable {

    void header(List<String> columns);

    /** One row; {@code values} line up with the header's columns and are not retained. */
    void row(FrameTime time, Object[] values);

    void flush();

    @Override
    void close();
}
