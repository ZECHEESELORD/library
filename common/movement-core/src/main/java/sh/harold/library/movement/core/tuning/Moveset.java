package sh.harold.library.movement.core.tuning;

import net.kyori.adventure.key.Key;
import sh.harold.library.movement.JumpPreset;
import sh.harold.library.movement.MoveKeys;
import sh.harold.library.movement.SlideVariant;
import sh.harold.library.reload.DataDecodeException;
import sh.harold.library.reload.core.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * What one body may do: the enabled moves and every tuning file. The server sends it to the client; both sides build
 * their engines and validators from it. With nothing enabled the body is vanilla.
 */
public record Moveset(
        Set<Key> enabled,
        CoreTuning core,
        JumpTuning jump,
        MantleTuning mantle,
        RollTuning roll,
        SlideTuning slide,
        WallJumpTuning wallJump
) {

    /** The tuning files in a fixed order, as the server writes them and the wire carries them. */
    public static final List<String> FILES = List.of(CoreTuning.FILE, JumpTuning.FILE, MantleTuning.FILE,
            RollTuning.FILE, SlideTuning.FILE, WallJumpTuning.FILE);

    private static final Map<String, String> DEFAULT_TEXTS = loadDefaultTexts();

    /** All five stock moves with 02's numbers. */
    public static final Moveset DEFAULT = decode(Set.copyOf(MoveKeys.STOCK), DEFAULT_TEXTS);

    public Moveset {
        enabled = Set.copyOf(new LinkedHashSet<>(enabled));
        Objects.requireNonNull(core, "core");
        Objects.requireNonNull(jump, "jump");
        Objects.requireNonNull(mantle, "mantle");
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(slide, "slide");
        Objects.requireNonNull(wallJump, "wallJump");
    }

    /** 02's numbers with nothing enabled: exactly vanilla. */
    public static Moveset none() {
        return DEFAULT.withEnabled(Set.of());
    }

    public boolean enables(Key move) {
        return enabled.contains(move) || (move.equals(MoveKeys.WALL_SLIDE) && enabled.contains(MoveKeys.WALL_JUMP));
    }

    public Moveset withEnabled(Set<Key> next) {
        return new Moveset(next, core, jump, mantle, roll, slide, wallJump);
    }

    public Moveset withJumpPreset(JumpPreset preset) {
        return new Moveset(enabled, core, jump.withPreset(preset), mantle, roll, slide, wallJump);
    }

    public Moveset withSlideVariant(SlideVariant variant) {
        return new Moveset(enabled, core, jump, mantle, roll, slide.withVariant(variant), wallJump);
    }

    /** The default text of one tuning file, as shipped in this jar. */
    public static String defaultText(String file) {
        String text = DEFAULT_TEXTS.get(file);
        if (text == null) {
            throw new IllegalArgumentException("unknown tuning file " + file);
        }
        return text;
    }

    public static Map<String, String> defaultTexts() {
        return DEFAULT_TEXTS;
    }

    /**
     * Decodes a moveset from tuning file texts by file name. Missing files fall back to the shipped defaults; a file
     * that fails to decode throws {@link DataDecodeException} with its path and line.
     */
    public static Moveset decode(Set<Key> enabled, Map<String, String> texts) {
        return new Moveset(
                enabled,
                CoreTuning.decode(parse(CoreTuning.FILE, texts)),
                JumpTuning.decode(parse(JumpTuning.FILE, texts)),
                MantleTuning.decode(parse(MantleTuning.FILE, texts)),
                RollTuning.decode(parse(RollTuning.FILE, texts)),
                SlideTuning.decode(parse(SlideTuning.FILE, texts)),
                WallJumpTuning.decode(parse(WallJumpTuning.FILE, texts)));
    }

    private static sh.harold.library.reload.JsonValue parse(String file, Map<String, String> texts) {
        String text = texts.get(file);
        return JsonReader.parse(text != null ? text : DEFAULT_TEXTS.get(file));
    }

    private static Map<String, String> loadDefaultTexts() {
        Map<String, String> texts = new LinkedHashMap<>();
        for (String file : FILES) {
            String path = "/sh/harold/library/movement/core/defaults/" + file;
            try (InputStream stream = Moveset.class.getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IllegalStateException("missing default tuning " + path);
                }
                texts.put(file, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return Map.copyOf(texts);
    }
}
