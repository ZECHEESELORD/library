package sh.harold.library.scoreboard.core;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import sh.harold.library.scoreboard.ScoreboardContext;
import sh.harold.library.scoreboard.ScoreboardFrame;
import sh.harold.library.scoreboard.ScoreboardLine;
import sh.harold.library.scoreboard.ScoreboardSection;
import sh.harold.library.scoreboard.ScoreboardService;
import sh.harold.library.scoreboard.ScoreboardSpec;
import sh.harold.library.scoreboard.TransientPlacement;
import sh.harold.library.scoreboard.TransientSectionSpec;
import sh.harold.library.tick.InstanceConflictPolicy;
import sh.harold.library.tick.KeyedHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class StandardScoreboardService implements ScoreboardService {
    private final Map<Key, ScoreboardSpec> specs = new LinkedHashMap<>();
    private final Map<UUID, ViewerSession> sessions = new HashMap<>();

    private long currentTick;
    private long nextGeneration = 1L;
    private boolean closed;

    @Override
    public void register(ScoreboardSpec spec) {
        ensureOpen();
        ScoreboardSpec value = Objects.requireNonNull(spec, "spec");
        specs.put(value.key(), value);
    }

    @Override
    public void unregister(Key key) {
        ensureOpen();
        Key value = Objects.requireNonNull(key, "key");
        specs.remove(value);
        for (ViewerSession session : sessions.values()) {
            if (value.equals(session.activeKey)) {
                session.clear();
            }
        }
        sessions.entrySet().removeIf(entry -> entry.getValue().empty());
    }

    @Override
    public void show(UUID viewerId, Key scoreboardKey) {
        ensureOpen();
        UUID viewer = Objects.requireNonNull(viewerId, "viewerId");
        Key key = Objects.requireNonNull(scoreboardKey, "scoreboardKey");
        if (!specs.containsKey(key)) {
            throw new IllegalArgumentException("unknown scoreboard: " + key);
        }

        ViewerSession session = sessions.computeIfAbsent(viewer, ignored -> new ViewerSession());
        if (!key.equals(session.activeKey)) {
            session.clearSectionState();
        }
        session.activeKey = key;
    }

    @Override
    public void hide(UUID viewerId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.clear();
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public void refresh(UUID viewerId) {
        ensureOpen();
        Objects.requireNonNull(viewerId, "viewerId");
    }

    @Override
    public void clearViewer(UUID viewerId) {
        ensureOpen();
        sessions.remove(Objects.requireNonNull(viewerId, "viewerId"));
    }

    @Override
    public void overrideTitle(UUID viewerId, Component title) {
        ensureOpen();
        ViewerSession session = activeSession(viewerId, "override scoreboard titles");
        session.titleOverride = Objects.requireNonNull(title, "title");
    }

    @Override
    public void clearTitleOverride(UUID viewerId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.titleOverride = null;
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public void overrideSection(UUID viewerId, String sectionId, ScoreboardSection replacement) {
        ensureOpen();
        String id = requireSectionId(sectionId);
        ViewerSession session = activeSession(viewerId, "override scoreboard sections");
        session.overrides.put(id, Objects.requireNonNull(replacement, "replacement"));
        session.hiddenSections.remove(id);
    }

    @Override
    public void clearSectionOverride(UUID viewerId, String sectionId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.overrides.remove(requireSectionId(sectionId));
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public void hideSection(UUID viewerId, String sectionId) {
        ensureOpen();
        String id = requireSectionId(sectionId);
        ViewerSession session = activeSession(viewerId, "hide scoreboard sections");
        session.hiddenSections.add(id);
    }

    @Override
    public void showSection(UUID viewerId, String sectionId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.hiddenSections.remove(requireSectionId(sectionId));
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public KeyedHandle pushTransient(UUID viewerId, TransientSectionSpec spec) {
        ensureOpen();
        UUID viewer = Objects.requireNonNull(viewerId, "viewerId");
        TransientSectionSpec value = Objects.requireNonNull(spec, "spec");
        ViewerSession session = activeSession(viewer, "push scoreboard transients");
        session.purgeExpired(currentTick);

        ActiveTransient existing = session.transients.get(value.key());
        if (existing != null) {
            if (value.conflictPolicy() == InstanceConflictPolicy.REJECT) {
                return inactiveHandle(value.key());
            }
            if (value.conflictPolicy() == InstanceConflictPolicy.REFRESH) {
                existing.refresh(currentTick);
                return new Handle(viewer, value.key(), existing.generation());
            }
        }

        ActiveTransient active = new ActiveTransient(value, nextGeneration++, currentTick);
        session.transients.put(value.key(), active);
        return new Handle(viewer, value.key(), active.generation());
    }

    @Override
    public void clearTransient(UUID viewerId, Key key) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.transients.remove(Objects.requireNonNull(key, "key"));
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public void clearTransients(UUID viewerId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session != null) {
            session.transients.clear();
            if (session.empty()) {
                sessions.remove(viewerId);
            }
        }
    }

    @Override
    public void advance() {
        ensureOpen();
        currentTick = Math.addExact(currentTick, 1L);
        purgeExpired();
    }

    @Override
    public Optional<ScoreboardFrame> render(UUID viewerId) {
        ensureOpen();
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session == null || session.activeKey == null) {
            return Optional.empty();
        }

        ScoreboardSpec spec = specs.get(session.activeKey);
        if (spec == null) {
            return Optional.empty();
        }

        session.purgeExpired(currentTick);
        List<ResolvedSection> sections = resolveSections(spec, session);
        applyTransients(sections, session);

        ScoreboardContext context = new ScoreboardContext(viewerId, spec.key(), currentTick);
        List<Component> bodyLines = renderBody(sections, context);
        List<Component> lines = applyLineBudget(bodyLines, spec.footerLines(), spec.maxLines());
        List<ScoreboardLine> indexedLines = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            indexedLines.add(new ScoreboardLine(index, lines.get(index)));
        }
        Component title = session.titleOverride != null ? session.titleOverride : spec.title();
        return Optional.of(new ScoreboardFrame(spec.key(), title, indexedLines));
    }

    @Override
    public void close() {
        closed = true;
        specs.clear();
        sessions.clear();
    }

    long currentTick() {
        return currentTick;
    }

    private List<ResolvedSection> resolveSections(ScoreboardSpec spec, ViewerSession session) {
        List<ResolvedSection> resolved = new ArrayList<>(spec.sections().size());
        for (ScoreboardSection section : spec.sections()) {
            String slotId = section.id();
            if (session.hiddenSections.contains(slotId)) {
                continue;
            }
            resolved.add(new ResolvedSection(slotId, session.overrides.getOrDefault(slotId, section)));
        }
        return resolved;
    }

    private void applyTransients(List<ResolvedSection> sections, ViewerSession session) {
        for (ActiveTransient transientSection : session.transients.values()) {
            TransientSectionSpec spec = transientSection.spec();
            ResolvedSection resolved = new ResolvedSection(
                    spec.placement() == TransientPlacement.REPLACE_SECTION ? spec.targetSectionId() : spec.section().id(),
                    spec.section()
            );
            switch (spec.placement()) {
                case TOP -> sections.add(0, resolved);
                case BOTTOM -> sections.add(resolved);
                case BEFORE_SECTION -> {
                    int index = indexOfSlot(sections, spec.targetSectionId());
                    if (index >= 0) {
                        sections.add(index, resolved);
                    }
                }
                case AFTER_SECTION -> {
                    int index = indexOfSlot(sections, spec.targetSectionId());
                    if (index >= 0) {
                        sections.add(index + 1, resolved);
                    }
                }
                case REPLACE_SECTION -> {
                    int index = indexOfSlot(sections, spec.targetSectionId());
                    if (index >= 0) {
                        sections.set(index, resolved);
                    }
                }
            }
        }
    }

    private List<Component> renderBody(List<ResolvedSection> sections, ScoreboardContext context) {
        List<Component> lines = new ArrayList<>();
        for (ResolvedSection section : sections) {
            List<Component> rendered = section.section().content().render(context);
            if (rendered == null) {
                throw new IllegalStateException("scoreboard section returned null lines: " + section.section().id());
            }
            for (Component line : rendered) {
                lines.add(Objects.requireNonNull(line, "scoreboard line"));
            }
        }
        return lines;
    }

    private static List<Component> applyLineBudget(List<Component> bodyLines, List<Component> footerLines, int maxLines) {
        int bodyLimit = maxLines - footerLines.size();
        List<Component> lines = new ArrayList<>(Math.min(bodyLines.size(), bodyLimit) + footerLines.size());
        for (int index = 0; index < bodyLines.size() && index < bodyLimit; index++) {
            lines.add(bodyLines.get(index));
        }
        lines.addAll(footerLines);
        return lines;
    }

    private static int indexOfSlot(List<ResolvedSection> sections, String slotId) {
        for (int index = 0; index < sections.size(); index++) {
            if (sections.get(index).slotId().equals(slotId)) {
                return index;
            }
        }
        return -1;
    }

    private void purgeExpired() {
        Iterator<Map.Entry<UUID, ViewerSession>> sessionsIterator = sessions.entrySet().iterator();
        while (sessionsIterator.hasNext()) {
            ViewerSession session = sessionsIterator.next().getValue();
            session.purgeExpired(currentTick);
            if (session.empty()) {
                sessionsIterator.remove();
            }
        }
    }

    private boolean active(UUID viewerId, Key key, long generation) {
        if (closed) {
            return false;
        }
        ViewerSession session = sessions.get(viewerId);
        if (session == null) {
            return false;
        }
        session.purgeExpired(currentTick);
        ActiveTransient transientSection = session.transients.get(key);
        return transientSection != null && transientSection.generation() == generation;
    }

    private void clear(UUID viewerId, Key key, long generation) {
        if (closed) {
            return;
        }
        ViewerSession session = sessions.get(viewerId);
        if (session == null) {
            return;
        }
        session.purgeExpired(currentTick);
        ActiveTransient transientSection = session.transients.get(key);
        if (transientSection != null && transientSection.generation() == generation) {
            session.transients.remove(key);
        }
        if (session.empty()) {
            sessions.remove(viewerId);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("Scoreboard service is closed");
        }
    }

    private ViewerSession activeSession(UUID viewerId, String action) {
        ViewerSession session = sessions.get(Objects.requireNonNull(viewerId, "viewerId"));
        if (session == null || session.activeKey == null) {
            throw new IllegalStateException("Cannot " + action + " without an active scoreboard");
        }
        return session;
    }

    private static String requireSectionId(String sectionId) {
        String value = Objects.requireNonNull(sectionId, "sectionId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("section id must not be blank");
        }
        return value;
    }

    private static KeyedHandle inactiveHandle(Key key) {
        return new KeyedHandle() {
            @Override
            public Key key() {
                return key;
            }

            @Override
            public boolean active() {
                return false;
            }

            @Override
            public void close() {
            }
        };
    }

    private static final class ViewerSession {
        private Key activeKey;
        private Component titleOverride;
        private final Map<String, ScoreboardSection> overrides = new HashMap<>();
        private final Set<String> hiddenSections = new HashSet<>();
        private final Map<Key, ActiveTransient> transients = new LinkedHashMap<>();

        private void clear() {
            activeKey = null;
            clearSectionState();
        }

        private void clearSectionState() {
            titleOverride = null;
            overrides.clear();
            hiddenSections.clear();
            transients.clear();
        }

        private boolean empty() {
            return activeKey == null && titleOverride == null && overrides.isEmpty() && hiddenSections.isEmpty() && transients.isEmpty();
        }

        private void purgeExpired(long tick) {
            transients.values().removeIf(active -> active.expired(tick));
        }
    }

    private record ResolvedSection(String slotId, ScoreboardSection section) {
    }

    private static final class ActiveTransient {
        private final TransientSectionSpec spec;
        private final long generation;
        private long startTick;

        private ActiveTransient(TransientSectionSpec spec, long generation, long startTick) {
            this.spec = spec;
            this.generation = generation;
            this.startTick = startTick;
        }

        private TransientSectionSpec spec() {
            return spec;
        }

        private long generation() {
            return generation;
        }

        private void refresh(long tick) {
            startTick = tick;
        }

        private boolean expired(long tick) {
            return Math.max(0L, tick - startTick) >= spec.ttlTicks();
        }
    }

    private final class Handle implements KeyedHandle {
        private final UUID viewerId;
        private final Key key;
        private final long generation;

        private Handle(UUID viewerId, Key key, long generation) {
            this.viewerId = viewerId;
            this.key = key;
            this.generation = generation;
        }

        @Override
        public Key key() {
            return key;
        }

        @Override
        public boolean active() {
            return StandardScoreboardService.this.active(viewerId, key, generation);
        }

        @Override
        public void close() {
            StandardScoreboardService.this.clear(viewerId, key, generation);
        }
    }
}
