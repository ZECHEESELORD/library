package sh.harold.creative.library.metrics.prometheus;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.metrics.CounterMetric;
import sh.harold.creative.library.metrics.LabelKey;
import sh.harold.creative.library.metrics.MetricLabels;
import sh.harold.creative.library.metrics.Metrics;
import sh.harold.creative.library.metrics.TimerMetric;
import sh.harold.creative.library.metrics.core.StandardTelemetry;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusFormatterTest {

    private static final LabelKey STATUS = Metrics.label("status", "success", "failure");

    @Test
    void formatsCountersAndHistograms() {
        CounterMetric counter = Metrics.counter("chunk_jobs_total", "Tracks chunk jobs", STATUS);
        TimerMetric timer = Metrics.timer("chunk_generation_seconds", "Tracks chunk generation latency", STATUS);
        StandardTelemetry telemetry = new StandardTelemetry();
        telemetry.counter(counter).increment(MetricLabels.of(STATUS, "success"));
        telemetry.timer(timer).recordNanos(5_000_000L, MetricLabels.of(STATUS, "success"));

        String output = new PrometheusFormatter().format(telemetry);

        assertTrue(output.contains("# HELP chunk_jobs_total Tracks chunk jobs"));
        assertTrue(output.contains("# TYPE chunk_jobs_total counter"));
        assertTrue(output.contains("chunk_jobs_total{status=\"success\"} 1.0"));
        assertTrue(output.contains("# TYPE chunk_generation_seconds histogram"));
        assertTrue(output.contains("chunk_generation_seconds_bucket{status=\"success\",le=\"0.005\"} 1"));
        assertTrue(output.contains("chunk_generation_seconds_count{status=\"success\"} 1"));
    }

    @Test
    void escapesLabelValuesAndHelpText() {
        CounterMetric counter = Metrics.counter("message_events_total", "Tracks \"message\" events\nsafely", Metrics.label("channel"));
        StandardTelemetry telemetry = new StandardTelemetry();
        telemetry.unsafe().increment(counter, sh.harold.creative.library.metrics.UnsafeMetricLabels.of("channel", "ops\"room"));

        String output = new PrometheusFormatter().format(telemetry);

        assertTrue(output.contains("Tracks \"message\" events\\nsafely"));
        assertTrue(output.contains("channel=\"ops\\\"room\""));
    }
}
