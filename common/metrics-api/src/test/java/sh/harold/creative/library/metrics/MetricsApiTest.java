package sh.harold.creative.library.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsApiTest {

    @Test
    void timerUsesSecondsUnitAndDefaultBuckets() {
        TimerMetric metric = Metrics.timer("chunk_generation_seconds", "Tracks chunk generation latency");

        assertEquals("seconds", metric.unit());
        assertEquals(Metrics.latencyBucketsSeconds(), metric.buckets());
    }

    @Test
    void duplicateLabelKeysAreRejected() {
        LabelKey status = Metrics.label("status", "success", "failure");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Metrics.counter("chunk_jobs_total", "Counts chunk jobs", status, status)
        );

        assertTrue(exception.getMessage().contains("Duplicate label key"));
    }

    @Test
    void metricLabelsRejectDuplicateKeys() {
        LabelKey phase = Metrics.label("phase", "start", "finish");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> MetricLabels.builder()
                        .put(phase, "start")
                        .put(phase, "finish")
                        .build()
        );

        assertTrue(exception.getMessage().contains("Duplicate label key"));
    }
}
