package sh.harold.creative.library.menu.core;

import net.kyori.adventure.text.Component;
import sh.harold.creative.library.menu.Menu;
import sh.harold.creative.library.menu.MenuFrame;
import sh.harold.creative.library.menu.MenuGeometry;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.Collections;
import java.util.function.Function;

final class StandardMenu implements Menu {

    private final Component title;
    private final MenuGeometry geometry;
    private final int rows;
    private final String initialFrameId;
    private final Set<String> frameIds;
    private final Function<String, MenuFrame> frameResolver;
    private final ConcurrentMap<String, MenuFrame> frames = new ConcurrentHashMap<>();

    StandardMenu(Component title, MenuGeometry geometry, int rows, String initialFrameId,
                 Set<String> frameIds, Function<String, MenuFrame> frameResolver) {
        this.title = Objects.requireNonNull(title, "title");
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.rows = rows;
        this.initialFrameId = Objects.requireNonNull(initialFrameId, "initialFrameId");
        this.frameIds = Collections.unmodifiableSet(Objects.requireNonNull(frameIds, "frameIds"));
        this.frameResolver = Objects.requireNonNull(frameResolver, "frameResolver");
    }

    @Override
    public Component title() {
        return title;
    }

    @Override
    public MenuGeometry geometry() {
        return geometry;
    }

    @Override
    public int rows() {
        return rows;
    }

    @Override
    public String initialFrameId() {
        return initialFrameId;
    }

    @Override
    public Set<String> frameIds() {
        return frameIds;
    }

    @Override
    public MenuFrame frame(String frameId) {
        if (!frameIds.contains(frameId)) {
            throw new IllegalArgumentException("Unknown menu frame: " + frameId);
        }
        return frames.computeIfAbsent(frameId, id -> {
            MenuFrame frame = Objects.requireNonNull(frameResolver.apply(id), "frameResolver");
            MenuValidator.validateFrame(frame, rows, title, id);
            return frame;
        });
    }
}
