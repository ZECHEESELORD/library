package sh.harold.creative.library.data.core;

import sh.harold.creative.library.data.DataApi;
import sh.harold.creative.library.data.DataNamespace;
import sh.harold.creative.library.data.SharedDataAccess;
import sh.harold.creative.library.data.SharedDataProvider;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class ConfiguredSharedDataProvider implements SharedDataProvider {

    private final DataApi dataApi;
    private final Function<String, String> defaultNamespaceResolver;
    private final Function<String, Collection<String>> extraNamespacesResolver;

    public ConfiguredSharedDataProvider(DataApi dataApi,
                                        Function<String, String> defaultNamespaceResolver,
                                        Function<String, Collection<String>> extraNamespacesResolver) {
        this.dataApi = Objects.requireNonNull(dataApi, "dataApi");
        this.defaultNamespaceResolver = Objects.requireNonNull(defaultNamespaceResolver, "defaultNamespaceResolver");
        this.extraNamespacesResolver = Objects.requireNonNull(extraNamespacesResolver, "extraNamespacesResolver");
    }

    @Override
    public SharedDataAccess access(String callerId) {
        String defaultNamespace = requireName(defaultNamespaceResolver.apply(requireName(callerId)));
        Set<String> allowed = new LinkedHashSet<>();
        allowed.add(defaultNamespace);
        Collection<String> extras = extraNamespacesResolver.apply(callerId);
        if (extras != null) {
            for (String extra : extras) {
                allowed.add(requireName(extra));
            }
        }
        return new AccessView(defaultNamespace, Set.copyOf(allowed));
    }

    private static String requireName(String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value cannot be blank");
        }
        return value;
    }

    private final class AccessView implements SharedDataAccess {

        private final String defaultNamespace;
        private final Set<String> allowedNamespaces;

        private AccessView(String defaultNamespace, Set<String> allowedNamespaces) {
            this.defaultNamespace = defaultNamespace;
            this.allowedNamespaces = allowedNamespaces;
        }

        @Override
        public DataNamespace defaultNamespace() {
            return dataApi.namespace(defaultNamespace);
        }

        @Override
        public Optional<DataNamespace> namespace(String name) {
            String normalized = requireName(name);
            if (!allowedNamespaces.contains(normalized)) {
                return Optional.empty();
            }
            return Optional.of(dataApi.namespace(normalized));
        }
    }
}
