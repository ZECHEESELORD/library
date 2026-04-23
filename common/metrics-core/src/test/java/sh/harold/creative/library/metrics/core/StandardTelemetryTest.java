package sh.harold.creative.library.metrics.core;

import org.junit.jupiter.api.Test;
import sh.harold.creative.library.metrics.CounterMetric;
import sh.harold.creative.library.metrics.GaugeMetric;
import sh.harold.creative.library.metrics.HistogramMetric;
import sh.harold.creative.library.metrics.LabelKey;
import sh.harold.creative.library.metrics.MetricLabels;
import sh.harold.creative.library.metrics.Metrics;
import sh.harold.creative.library.metrics.TimerMetric;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardTelemetryTest {

    private static final LabelKey STATUS = Metrics.label("status", "success", "failure");

    @Test
    void counterAggregatesConcurrentWrites() throws Exception {
        CounterMetric metric = Metrics.counter("chunk_jobs_total", "Tracks chunk jobs", STATUS);
        StandardTelemetry telemetry = new StandardTelemetry();
        var executor = Executors.newFixedThreadPool(4);
        CountDownLatch latch = new CountDownLatch(40);

        for (int i = 0; i < 40; i++) {
            executor.submit(() -> {
                telemetry.counter(metric).increment(MetricLabels.of(STATUS, "success"));
                latch.countDown();
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdownNow();

        MetricSnapshotSource.ScalarMetricSnapshot snapshot = assertInstanceOf(
                MetricSnapshotSource.ScalarMetricSnapshot.class,
                telemetry.snapshot().iterator().next()
        );
        assertEquals(40.0, snapshot.samples().getFirst().value());
    }

    @Test
    void safePathRejectsOutOfBoundsLabelValues() {
        CounterMetric metric = Metrics.counter("chunk_jobs_total", "Tracks chunk jobs", STATUS);
        StandardTelemetry telemetry = new StandardTelemetry();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> telemetry.counter(metric).increment(MetricLabels.of(STATUS, "queued"))
        );

        assertTrue(exception.getMessage().contains("does not allow value"));
    }

    @Test
    void unsafePathAllowsDeclaredButUnboundedValues() {
        CounterMetric metric = Metrics.counter("player_events_total", "Tracks player events", Metrics.label("player"));
        StandardTelemetry telemetry = new StandardTelemetry();

        telemetry.unsafe().increment(metric, sh.harold.creative.library.metrics.UnsafeMetricLabels.of("player", "harold"));

        MetricSnapshotSource.ScalarMetricSnapshot snapshot = assertInstanceOf(
                MetricSnapshotSource.ScalarMetricSnapshot.class,
                telemetry.snapshot().iterator().next()
        );
        assertEquals(List.of("harold"), snapshot.samples().getFirst().labelValues());
    }

    @Test
    void timerScopeRecordsDurations() {
        TimerMetric metric = Metrics.timer("chunk_generation_seconds", "Tracks chunk generation latency", STATUS);
        StandardTelemetry telemetry = new StandardTelemetry();

        telemetry.observe(metric, MetricLabels.of(STATUS, "success"), () -> {
        });

        MetricSnapshotSource.HistogramMetricSnapshot snapshot = assertInstanceOf(
                MetricSnapshotSource.HistogramMetricSnapshot.class,
                telemetry.snapshot().iterator().next()
        );
        assertEquals(1L, snapshot.samples().getFirst().count());
        assertTrue(snapshot.samples().getFirst().sum() >= 0.0);
    }

    @Test
    void histogramRecordsBuckets() {
        HistogramMetric metric = Metrics.histogram(
                "queue_depth",
                "Tracks queue depth",
                "items",
                List.of(1.0, 5.0, 10.0)
        );
        StandardTelemetry telemetry = new StandardTelemetry();

        telemetry.histogram(metric).record(4.0);
        telemetry.histogram(metric).record(12.0);

        MetricSnapshotSource.HistogramMetricSnapshot snapshot = assertInstanceOf(
                MetricSnapshotSource.HistogramMetricSnapshot.class,
                telemetry.snapshot().iterator().next()
        );
        assertEquals(List.of(0L, 1L, 0L), snapshot.samples().getFirst().bucketCounts());
        assertEquals(2L, snapshot.samples().getFirst().count());
    }

    @Test
    void observedGaugeCannotReuseManualSeries() {
        GaugeMetric metric = Metrics.gauge("players_online", "Tracks connected players", "players");
        StandardTelemetry telemetry = new StandardTelemetry();
        telemetry.gauge(metric).set(12.0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> telemetry.registerGauge(metric, MetricLabels.empty(), () -> 12.0)
        );

        assertTrue(exception.getMessage().contains("already has a registration"));
    }

    @Test
    void observedCounterRegistrationCanBeClosed() {
        CounterMetric metric = Metrics.counter("jvm_process_cpu_time_seconds_total", "Tracks CPU time");
        StandardTelemetry telemetry = new StandardTelemetry();
        var registration = telemetry.registerCounter(metric, MetricLabels.empty(), () -> 3.0);

        registration.close();

        MetricSnapshotSource.ScalarMetricSnapshot snapshot = assertInstanceOf(
                MetricSnapshotSource.ScalarMetricSnapshot.class,
                telemetry.snapshot().iterator().next()
        );
        assertTrue(snapshot.samples().isEmpty());
    }
}
