package sh.harold.creative.library.metrics.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JvmMetricsBinderTest {

    @Test
    void binderRegistersJvmMetrics() {
        StandardTelemetry telemetry = new StandardTelemetry();
        var registration = JvmMetricsBinder.bind(telemetry);

        Set<String> metricNames = telemetry.snapshot().stream()
                .map(MetricSnapshotSource.MetricSnapshot::name)
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(metricNames.contains("jvm_memory_used_bytes"));
        assertTrue(metricNames.contains("jvm_threads_live_threads"));
        assertTrue(metricNames.contains("jvm_process_uptime_seconds"));

        registration.close();

        assertFalse(telemetry.snapshot().isEmpty());
    }
}
