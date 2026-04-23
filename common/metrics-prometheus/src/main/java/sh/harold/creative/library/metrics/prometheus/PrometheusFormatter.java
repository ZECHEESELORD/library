package sh.harold.creative.library.metrics.prometheus;

import sh.harold.creative.library.metrics.core.MetricSnapshotSource;

import java.util.List;
import java.util.Locale;

public final class PrometheusFormatter {

    public static final String CONTENT_TYPE = "text/plain; version=0.0.4; charset=utf-8";

    public String format(MetricSnapshotSource source) {
        StringBuilder builder = new StringBuilder();
        for (MetricSnapshotSource.MetricSnapshot snapshot : source.snapshot()) {
            if (snapshot instanceof MetricSnapshotSource.ScalarMetricSnapshot scalar) {
                appendScalar(builder, scalar);
                continue;
            }
            if (snapshot instanceof MetricSnapshotSource.HistogramMetricSnapshot histogram) {
                appendHistogram(builder, histogram);
            }
        }
        return builder.toString();
    }

    private void appendScalar(StringBuilder builder, MetricSnapshotSource.ScalarMetricSnapshot snapshot) {
        appendFamilyHeader(builder, snapshot.name(), snapshot.help(), snapshot.type().name().toLowerCase(Locale.ROOT));
        for (MetricSnapshotSource.ScalarSample sample : snapshot.samples()) {
            appendSample(builder, snapshot.name(), snapshot.labelNames(), sample.labelValues(), sample.value());
        }
    }

    private void appendHistogram(StringBuilder builder, MetricSnapshotSource.HistogramMetricSnapshot snapshot) {
        appendFamilyHeader(builder, snapshot.name(), snapshot.help(), "histogram");
        List<Double> buckets = snapshot.buckets();
        for (MetricSnapshotSource.HistogramSample sample : snapshot.samples()) {
            long runningCount = 0L;
            for (int index = 0; index < buckets.size(); index++) {
                runningCount += sample.bucketCounts().get(index);
                appendSample(
                        builder,
                        snapshot.name() + "_bucket",
                        merge(snapshot.labelNames(), "le"),
                        merge(sample.labelValues(), formatDouble(buckets.get(index))),
                        runningCount
                );
            }
            appendSample(
                    builder,
                    snapshot.name() + "_bucket",
                    merge(snapshot.labelNames(), "le"),
                    merge(sample.labelValues(), "+Inf"),
                    sample.count()
            );
            appendSample(builder, snapshot.name() + "_sum", snapshot.labelNames(), sample.labelValues(), sample.sum());
            appendSample(builder, snapshot.name() + "_count", snapshot.labelNames(), sample.labelValues(), sample.count());
        }
    }

    private void appendFamilyHeader(StringBuilder builder, String name, String help, String type) {
        builder.append("# HELP ").append(name).append(' ').append(escapeHelp(help)).append('\n');
        builder.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private void appendSample(StringBuilder builder, String name, List<String> labelNames, List<String> labelValues, double value) {
        builder.append(name);
        appendLabels(builder, labelNames, labelValues);
        builder.append(' ').append(formatDouble(value)).append('\n');
    }

    private void appendSample(StringBuilder builder, String name, List<String> labelNames, List<String> labelValues, long value) {
        builder.append(name);
        appendLabels(builder, labelNames, labelValues);
        builder.append(' ').append(value).append('\n');
    }

    private void appendLabels(StringBuilder builder, List<String> labelNames, List<String> labelValues) {
        if (labelNames.isEmpty()) {
            return;
        }
        builder.append('{');
        for (int index = 0; index < labelNames.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(labelNames.get(index))
                    .append("=\"")
                    .append(escapeLabelValue(labelValues.get(index)))
                    .append('"');
        }
        builder.append('}');
    }

    private String escapeHelp(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private String escapeLabelValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String formatDouble(double value) {
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (value == Double.POSITIVE_INFINITY) {
            return "+Inf";
        }
        if (value == Double.NEGATIVE_INFINITY) {
            return "-Inf";
        }
        return Double.toString(value);
    }

    private List<String> merge(List<String> values, String extra) {
        return java.util.stream.Stream.concat(values.stream(), java.util.stream.Stream.of(extra)).toList();
    }
}
