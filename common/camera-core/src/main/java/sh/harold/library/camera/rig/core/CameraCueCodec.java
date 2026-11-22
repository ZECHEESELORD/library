package sh.harold.library.camera.rig.core;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import sh.harold.library.camera.rig.CameraChannel;
import sh.harold.library.camera.rig.cue.CameraCue;
import sh.harold.library.camera.rig.cue.ChannelCurve;
import sh.harold.library.camera.rig.cue.CueCurve;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.DataDecoder;
import sh.harold.library.reload.JsonValue;
import sh.harold.library.tick.InstanceConflictPolicy;
import sh.harold.library.tween.Easing;
import sh.harold.library.tween.EasingFunction;
import sh.harold.library.tween.Easings;
import sh.harold.library.tween.spring.SpringSpec;
import sh.harold.library.tween.track.Interpolation;
import sh.harold.library.tween.track.Track;
import sh.harold.library.tween.track.TrackKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Camera cues as JSON data, so cue tuning hot-reloads with the hot-reload service. Errors name the key and field.
 *
 * <pre>{@code
 * {
 *   "key": "movement:roll",
 *   "owner": "movement:moves",
 *   "release": { "ticks": 4, "easing": "quad_out" },
 *   "curves": [
 *     { "channel": "pitch", "unwound": true,
 *       "track": { "keys": [ { "t": 0, "v": 0, "interp": "quad_in" },
 *                            { "t": 5, "v": 180, "interp": "quad_out" },
 *                            { "t": 9, "v": 360 } ] },
 *       "reduced": { "pulse": { "amount": 25, "attack": 3, "hold": 1, "release": 5 } } },
 *     { "channel": "fov", "pulse": { "amount": 6, "attack": 3, "attack_easing": "quad_out", "release": 7 } },
 *     { "channel": "look_scale", "constant": { "amount": -0.8, "ticks": 10 } }
 *   ]
 * }
 * }</pre>
 *
 * <p>Curve forms: {@code track} (keys with {@code t}, {@code v}, optional {@code interp}: {@code hermite} (default),
 * {@code stepped} or an easing name; a cut key gives {@code left} and {@code right} instead of {@code v}; optional
 * {@code hold_ticks}), {@code pulse} ({@code amount}, {@code attack}, {@code attack_easing}, {@code hold} as ticks or
 * {@code "held"}, {@code release}, {@code release_easing}), {@code constant} ({@code amount}, {@code ticks}) and
 * {@code spring} ({@code frequency_hz} and {@code damping}, or {@code overshoot_percent} and {@code settle_ticks}; plus
 * {@code displacement} and {@code velocity}). Easings are {@link Easing} names in lower case or a cubic Bezier
 * {@code [x1, y1, x2, y2]}.
 */
public final class CameraCueCodec {

    /** Decodes one cue object. */
    public static final DataDecoder<CameraCue> CUE = CameraCueCodec::decodeCue;

    /** Decodes an object of named cues: {@code { "roll": { ...cue... }, "slide": { ... } }}, in file order. */
    public static final DataDecoder<Map<String, CameraCue>> CUE_MAP = root -> {
        Map<String, CameraCue> cues = new LinkedHashMap<>();
        root.asObject().members().forEach((name, value) -> cues.put(name, decodeCue(value)));
        return cues;
    };

    private CameraCueCodec() {
    }

    public static CameraCue decodeCue(JsonValue value) {
        JsonValue.JsonObject object = value.asObject();
        Key key = key(object.get("key"));
        CameraCue.Builder builder = CameraCue.builder(key);
        object.optional("owner").ifPresent(owner -> builder.owner(key(owner)));
        object.optional("release").ifPresent(release -> {
            JsonValue.JsonObject r = release.asObject();
            builder.release(nonNegative(r.get("ticks")), r.optional("easing").map(CameraCueCodec::easing)
                    .orElse(Easing.QUAD_OUT));
        });
        object.optional("conflict").ifPresent(conflict -> builder.conflict(conflict(conflict)));
        JsonValue.JsonArray curves = object.get("curves").asArray();
        if (curves.elements().isEmpty()) {
            throw new DataDecodeException(curves, "a cue needs at least one curve");
        }
        for (JsonValue entry : curves.elements()) {
            builder.curve(channelCurve(entry));
        }
        return builder.build();
    }

    public static ChannelCurve channelCurve(JsonValue value) {
        JsonValue.JsonObject object = value.asObject();
        JsonValue channelValue = object.get("channel");
        CameraChannel channel;
        try {
            channel = CameraChannel.fromId(channelValue.asString());
        } catch (IllegalArgumentException e) {
            throw new DataDecodeException(channelValue, e.getMessage());
        }
        ChannelCurve curve = ChannelCurve.of(channel, curve(object));
        if (object.optional("reduced").isPresent()) {
            curve = curve.withReduced(curve(object.get("reduced").asObject()));
        }
        if (object.getBoolean("unwound", false)) {
            if (channel != CameraChannel.PITCH) {
                throw new DataDecodeException(object.get("unwound"), "only pitch curves can be unwound");
            }
            curve = curve.unwoundPitch();
        }
        return curve;
    }

    /** The one curve form in {@code object}. */
    public static CueCurve curve(JsonValue.JsonObject object) {
        List<String> forms = new ArrayList<>();
        for (String form : List.of("track", "pulse", "constant", "spring")) {
            if (object.has(form)) {
                forms.add(form);
            }
        }
        if (forms.size() != 1) {
            throw new DataDecodeException(object, "expected exactly one of track, pulse, constant or spring, found "
                    + (forms.isEmpty() ? "none" : String.join(", ", forms)));
        }
        JsonValue body = object.get(forms.getFirst());
        try {
            return switch (forms.getFirst()) {
                case "track" -> track(body.asObject());
                case "pulse" -> pulse(body.asObject());
                case "constant" -> {
                    JsonValue.JsonObject c = body.asObject();
                    yield CueCurve.constant(c.get("amount").asDouble(), nonNegative(c.get("ticks")));
                }
                default -> spring(body.asObject());
            };
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new DataDecodeException(body, e.getMessage());
        }
    }

    private static CueCurve track(JsonValue.JsonObject object) {
        Track.Builder<Double> builder = Track.scalar();
        JsonValue.JsonArray keys = object.get("keys").asArray();
        if (keys.elements().isEmpty()) {
            throw new DataDecodeException(keys, "a track needs at least one key");
        }
        for (JsonValue entry : keys.elements()) {
            JsonValue.JsonObject key = entry.asObject();
            double t = key.get("t").asDouble();
            Interpolation interpolation = key.optional("interp").map(CameraCueCodec::interpolation)
                    .orElse(Interpolation.HERMITE);
            if (key.has("left") || key.has("right")) {
                builder.cut(t, key.get("left").asDouble(), key.get("right").asDouble(), interpolation);
                continue;
            }
            double v = key.get("v").asDouble();
            switch (interpolation) {
                case Interpolation.Hermite hermite -> builder.hermite(t, v);
                case Interpolation.Stepped stepped -> builder.stepped(t, v);
                case Interpolation.Eased eased -> builder.key(TrackKey.eased(t, v, eased.easing()));
            }
        }
        object.optional("hold_ticks").ifPresent(hold -> builder.holdTicks(hold.asInt()));
        return CueCurve.track(builder.build());
    }

    private static CueCurve pulse(JsonValue.JsonObject object) {
        double hold = object.optional("hold").map(h -> h instanceof JsonValue.JsonString s && s.value().equals("held")
                ? Double.POSITIVE_INFINITY : nonNegative(h)).orElse(0.0);
        return CueCurve.pulse(
                object.get("amount").asDouble(),
                object.optional("attack").map(CameraCueCodec::nonNegative).orElse(0.0),
                object.optional("attack_easing").map(CameraCueCodec::easing).orElse(Easing.QUAD_OUT),
                hold,
                object.optional("release").map(CameraCueCodec::nonNegative).orElse(0.0),
                object.optional("release_easing").map(CameraCueCodec::easing).orElse(Easing.QUAD_IN_OUT));
    }

    private static CueCurve spring(JsonValue.JsonObject object) {
        SpringSpec spec;
        if (object.has("frequency_hz")) {
            spec = new SpringSpec(object.get("frequency_hz").asDouble(), object.getDouble("damping", 1.0));
        } else {
            spec = SpringSpec.fromOvershoot(object.getDouble("overshoot_percent", 0.0),
                    object.get("settle_ticks").asDouble());
        }
        return CueCurve.spring(spec, object.getDouble("displacement", 0.0), object.getDouble("velocity", 0.0));
    }

    private static Interpolation interpolation(JsonValue value) {
        if (value instanceof JsonValue.JsonString string) {
            if (string.value().equals("hermite")) {
                return Interpolation.HERMITE;
            }
            if (string.value().equals("stepped")) {
                return Interpolation.STEPPED;
            }
        }
        return Interpolation.eased(easing(value));
    }

    static EasingFunction easing(JsonValue value) {
        if (value instanceof JsonValue.JsonArray array) {
            if (array.elements().size() != 4) {
                throw new DataDecodeException(value, "a cubic Bezier easing needs [x1, y1, x2, y2]");
            }
            double[] p = new double[4];
            for (int i = 0; i < 4; i++) {
                p[i] = array.elements().get(i).asDouble();
            }
            try {
                return Easings.cubicBezier(p[0], p[1], p[2], p[3]);
            } catch (IllegalArgumentException e) {
                throw new DataDecodeException(value, e.getMessage());
            }
        }
        String name = value.asString();
        try {
            return Easing.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DataDecodeException(value, "unknown easing '" + name + "'");
        }
    }

    private static InstanceConflictPolicy conflict(JsonValue value) {
        String name = value.asString();
        try {
            return InstanceConflictPolicy.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DataDecodeException(value, "unknown conflict policy '" + name + "'");
        }
    }

    private static Key key(JsonValue value) {
        try {
            return Key.key(value.asString());
        } catch (InvalidKeyException e) {
            throw new DataDecodeException(value, "invalid key '" + value.asString() + "'");
        }
    }

    private static double nonNegative(JsonValue value) {
        double number = value.asDouble();
        if (!(number >= 0.0) || !Double.isFinite(number)) {
            throw new DataDecodeException(value, "expected a non-negative number, found " + number);
        }
        return number;
    }
}
