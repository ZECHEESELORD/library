package sh.harold.creative.library.metrics.core;

import com.sun.management.OperatingSystemMXBean;
import sh.harold.creative.library.metrics.CounterMetric;
import sh.harold.creative.library.metrics.GaugeMetric;
import sh.harold.creative.library.metrics.LabelKey;
import sh.harold.creative.library.metrics.MetricLabels;
import sh.harold.creative.library.metrics.MetricRegistration;
import sh.harold.creative.library.metrics.Metrics;
import sh.harold.creative.library.metrics.Telemetry;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class JvmMetricsBinder {

    private static final LabelKey AREA = Metrics.label("area", "heap", "nonheap");
    private static final LabelKey COLLECTOR = Metrics.label("collector");

    private static final GaugeMetric MEMORY_USED_BYTES = Metrics.gauge("jvm_memory_used_bytes", "JVM memory currently in use", "bytes", AREA);
    private static final GaugeMetric MEMORY_COMMITTED_BYTES = Metrics.gauge("jvm_memory_committed_bytes", "JVM memory committed by the runtime", "bytes", AREA);
    private static final GaugeMetric MEMORY_MAX_BYTES = Metrics.gauge("jvm_memory_max_bytes", "Maximum JVM memory available", "bytes", AREA);
    private static final GaugeMetric THREADS_LIVE = Metrics.gauge("jvm_threads_live_threads", "Current live thread count", "threads");
    private static final GaugeMetric THREADS_DAEMON = Metrics.gauge("jvm_threads_daemon_threads", "Current daemon thread count", "threads");
    private static final GaugeMetric THREADS_PEAK = Metrics.gauge("jvm_threads_peak_threads", "Peak JVM thread count", "threads");
    private static final GaugeMetric PROCESS_UPTIME_SECONDS = Metrics.gauge("jvm_process_uptime_seconds", "Process uptime in seconds", "seconds");
    private static final GaugeMetric PROCESS_CPU_LOAD = Metrics.gauge("jvm_process_cpu_load_ratio", "Recent process CPU load ratio", "ratio");
    private static final CounterMetric PROCESS_CPU_TIME_SECONDS = Metrics.counter("jvm_process_cpu_time_seconds_total", "Total process CPU time in seconds", "seconds");
    private static final CounterMetric GC_COLLECTIONS_TOTAL = Metrics.counter("jvm_gc_collections_total", "Total garbage collection count", COLLECTOR);
    private static final CounterMetric GC_COLLECTION_SECONDS = Metrics.counter("jvm_gc_collection_seconds_total", "Total garbage collection time in seconds", "seconds", COLLECTOR);

    private JvmMetricsBinder() {
    }

    public static MetricRegistration bind(Telemetry telemetry) {
        MemoryMXBean memoryMxBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
        List<MetricRegistration> registrations = new ArrayList<>();

        registrations.add(registerMemoryUsage(telemetry, memoryMxBean, true));
        registrations.add(registerMemoryUsage(telemetry, memoryMxBean, false));
        registrations.add(telemetry.registerGauge(THREADS_LIVE, MetricLabels.empty(), threadMxBean::getThreadCount));
        registrations.add(telemetry.registerGauge(THREADS_DAEMON, MetricLabels.empty(), threadMxBean::getDaemonThreadCount));
        registrations.add(telemetry.registerGauge(THREADS_PEAK, MetricLabels.empty(), threadMxBean::getPeakThreadCount));
        registrations.add(telemetry.registerGauge(PROCESS_UPTIME_SECONDS, MetricLabels.empty(), () -> ManagementFactory.getRuntimeMXBean().getUptime() / 1_000.0));

        OperatingSystemMXBean operatingSystemMxBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        if (operatingSystemMxBean != null) {
            registrations.add(telemetry.registerGauge(PROCESS_CPU_LOAD, MetricLabels.empty(), operatingSystemMxBean::getProcessCpuLoad));
            registrations.add(telemetry.registerCounter(PROCESS_CPU_TIME_SECONDS, MetricLabels.empty(), () -> operatingSystemMxBean.getProcessCpuTime() / 1_000_000_000.0));
        }

        for (GarbageCollectorMXBean collectorMxBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            String normalizedCollector = normalizeCollectorName(collectorMxBean.getName());
            MetricLabels labels = MetricLabels.of(COLLECTOR, normalizedCollector);
            registrations.add(telemetry.registerCounter(GC_COLLECTIONS_TOTAL, labels, () -> Math.max(collectorMxBean.getCollectionCount(), 0L)));
            registrations.add(telemetry.registerCounter(GC_COLLECTION_SECONDS, labels, () -> Math.max(collectorMxBean.getCollectionTime(), 0L) / 1_000.0));
        }

        return MetricRegistration.composite(registrations);
    }

    private static MetricRegistration registerMemoryUsage(Telemetry telemetry, MemoryMXBean memoryMxBean, boolean heap) {
        String area = heap ? "heap" : "nonheap";
        MetricLabels labels = MetricLabels.of(AREA, area);
        return MetricRegistration.composite(
                telemetry.registerGauge(MEMORY_USED_BYTES, labels, () -> usage(memoryMxBean, heap).getUsed()),
                telemetry.registerGauge(MEMORY_COMMITTED_BYTES, labels, () -> usage(memoryMxBean, heap).getCommitted()),
                telemetry.registerGauge(MEMORY_MAX_BYTES, labels, () -> usage(memoryMxBean, heap).getMax())
        );
    }

    private static MemoryUsage usage(MemoryMXBean memoryMxBean, boolean heap) {
        return heap ? memoryMxBean.getHeapMemoryUsage() : memoryMxBean.getNonHeapMemoryUsage();
    }

    private static String normalizeCollectorName(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
    }
}
