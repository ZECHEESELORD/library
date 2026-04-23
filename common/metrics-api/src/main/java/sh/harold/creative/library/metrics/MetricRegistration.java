package sh.harold.creative.library.metrics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface MetricRegistration extends AutoCloseable {

    @Override
    void close();

    static MetricRegistration noop() {
        return () -> {
        };
    }

    static MetricRegistration composite(Collection<? extends MetricRegistration> registrations) {
        List<MetricRegistration> copy = List.copyOf(registrations);
        return () -> {
            List<RuntimeException> failures = new ArrayList<>();
            for (MetricRegistration registration : copy) {
                try {
                    registration.close();
                } catch (RuntimeException exception) {
                    failures.add(exception);
                }
            }
            if (!failures.isEmpty()) {
                RuntimeException failure = failures.removeFirst();
                failures.forEach(failure::addSuppressed);
                throw failure;
            }
        };
    }

    static MetricRegistration composite(MetricRegistration... registrations) {
        return composite(List.of(registrations));
    }
}
