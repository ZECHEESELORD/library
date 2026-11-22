package sh.harold.library.net.protocol;

import java.util.regex.Pattern;

/**
 * Payload id rules: {@code creative_library:<feature>/<name>}, with a feature of {@code [a-z0-9_]+} and a name of
 * {@code [a-z0-9_]+} segments separated by {@code /}.
 */
public final class PayloadIds {

    public static final String NAMESPACE = "creative_library";

    private static final Pattern FEATURE = Pattern.compile("[a-z0-9_]+");
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+(/[a-z0-9_]+)*");

    private PayloadIds() {
    }

    public static String requireFeature(String feature) {
        if (feature == null || !FEATURE.matcher(feature).matches()) {
            throw new IllegalArgumentException("feature id '" + feature + "' must match [a-z0-9_]+");
        }
        return feature;
    }

    public static String requireName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("payload name '" + name + "' must match [a-z0-9_]+(/[a-z0-9_]+)*");
        }
        return name;
    }

    /** The id path {@code <feature>/<name>}. */
    public static String path(String feature, String name) {
        return requireFeature(feature) + "/" + requireName(name);
    }

    /** The full id {@code creative_library:<feature>/<name>}. */
    public static String id(String feature, String name) {
        return NAMESPACE + ":" + path(feature, name);
    }
}
