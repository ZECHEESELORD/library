package sh.harold.creative.library.scoreboard.minestom;

import net.kyori.adventure.key.Key;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import sh.harold.creative.library.scoreboard.ScoreboardFrame;
import sh.harold.creative.library.scoreboard.ScoreboardLine;
import sh.harold.creative.library.scoreboard.ScoreboardSection;
import sh.harold.creative.library.scoreboard.ScoreboardService;
import sh.harold.creative.library.scoreboard.ScoreboardSpec;
import sh.harold.creative.library.scoreboard.TransientSectionSpec;
import sh.harold.creative.library.scoreboard.core.StandardScoreboardService;
import sh.harold.creative.library.tick.KeyedHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MinestomScoreboardPlatform implements AutoCloseable {
    private final EventNode<Event> parentNode;
    private final EventNode<Event> runtimeNode;
    private final Scheduler scheduler;
    private final StandardScoreboardService scoreboards = new StandardScoreboardService();
    private final Map<UUID, MinestomScoreboardSession> sessions = new ConcurrentHashMap<>();

    private Task tickTask;
    private volatile boolean closed;

    public MinestomScoreboardPlatform() {
        this(MinecraftServer.getGlobalEventHandler(), MinecraftServer.getSchedulerManager());
    }

    public MinestomScoreboardPlatform(EventNode<Event> parentNode) {
        this(parentNode, MinecraftServer.getSchedulerManager());
    }

    public MinestomScoreboardPlatform(EventNode<Event> parentNode, Scheduler scheduler) {
        this.parentNode = Objects.requireNonNull(parentNode, "parentNode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.runtimeNode = EventNode.all("scoreboard-runtime-" + UUID.randomUUID());
        runtimeNode.addListener(PlayerDisconnectEvent.class, event -> cleanup(event.getPlayer().getUuid()));
        runtimeNode.addListener(RemoveEntityFromInstanceEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                cleanup(player.getUuid());
            }
        });
        this.parentNode.addChild(runtimeNode);
    }

    public ScoreboardService service() {
        return scoreboards;
    }

    public void register(ScoreboardSpec spec) {
        scoreboards.register(spec);
    }

    public void unregister(Key key) {
        scoreboards.unregister(key);
        reconcileAll();
    }

    public void show(Player player, Key scoreboardKey) {
        Player target = Objects.requireNonNull(player, "player");
        MinestomScoreboardSession session = session(target);
        target.acquirable().sync(ownedPlayer -> {
            scoreboards.show(ownedPlayer.getUuid(), Objects.requireNonNull(scoreboardKey, "scoreboardKey"));
            session.reconcileOwned(ownedPlayer);
        });
        ensureTicking();
    }

    public void hide(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.hide(target.getUuid());
        MinestomScoreboardSession session = sessions.remove(target.getUuid());
        if (session != null) {
            session.close();
        }
        cancelTickingIfIdle();
    }

    public void clearViewer(Player player) {
        hide(player);
    }

    public void overrideSection(Player player, String sectionId, ScoreboardSection replacement) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.overrideSection(target.getUuid(), sectionId, replacement);
        reconcile(target);
    }

    public void clearSectionOverride(Player player, String sectionId) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearSectionOverride(target.getUuid(), sectionId);
        reconcile(target);
    }

    public void hideSection(Player player, String sectionId) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.hideSection(target.getUuid(), sectionId);
        reconcile(target);
    }

    public void showSection(Player player, String sectionId) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.showSection(target.getUuid(), sectionId);
        reconcile(target);
    }

    public KeyedHandle pushTransient(Player player, TransientSectionSpec spec) {
        Player target = Objects.requireNonNull(player, "player");
        KeyedHandle handle = scoreboards.pushTransient(target.getUuid(), spec);
        reconcile(target);
        ensureTicking();
        return handle;
    }

    public void clearTransient(Player player, Key key) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearTransient(target.getUuid(), key);
        reconcile(target);
    }

    public void clearTransients(Player player) {
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearTransients(target.getUuid());
        reconcile(target);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        parentNode.removeChild(runtimeNode);
        Task task = tickTask;
        tickTask = null;
        if (task != null) {
            task.cancel();
        }
        List<MinestomScoreboardSession> currentSessions = new ArrayList<>(sessions.values());
        sessions.clear();
        currentSessions.forEach(MinestomScoreboardSession::close);
        scoreboards.close();
    }

    private MinestomScoreboardSession session(Player player) {
        return sessions.compute(player.getUuid(), (ignored, existing) -> {
            if (existing == null || existing.closed()) {
                return new MinestomScoreboardSession(player);
            }
            return existing;
        });
    }

    private void reconcile(Player player) {
        MinestomScoreboardSession session = sessions.get(player.getUuid());
        if (session != null) {
            player.acquirable().sync(session::reconcileOwned);
        }
        cancelTickingIfIdle();
    }

    private void reconcileAll() {
        for (MinestomScoreboardSession session : List.copyOf(sessions.values())) {
            session.reconcile();
        }
        cancelTickingIfIdle();
    }

    private void cleanup(UUID playerId) {
        scoreboards.clearViewer(playerId);
        MinestomScoreboardSession session = sessions.remove(playerId);
        if (session != null) {
            session.close();
        }
        cancelTickingIfIdle();
    }

    private void ensureTicking() {
        if (tickTask == null && !sessions.isEmpty()) {
            tickTask = scheduler.scheduleTask(this::tick, TaskSchedule.tick(1), TaskSchedule.tick(1));
        }
    }

    private void cancelTickingIfIdle() {
        if (tickTask != null && sessions.isEmpty()) {
            tickTask.cancel();
            tickTask = null;
        }
    }

    private void tick() {
        if (closed) {
            return;
        }
        scoreboards.advance();
        reconcileAll();
    }

    private static String lineId(int index) {
        return "clib_sb_" + index;
    }

    private final class MinestomScoreboardSession {
        private final Player player;
        private final UUID playerId;

        private Sidebar sidebar;
        private int renderedLineCount;
        private volatile boolean closed;

        private MinestomScoreboardSession(Player player) {
            this.player = player;
            this.playerId = player.getUuid();
        }

        private void reconcile() {
            if (closed) {
                return;
            }
            if (!player.isOnline()) {
                cleanup(playerId);
                return;
            }
            player.acquirable().sync(this::reconcileOwned);
        }

        private void reconcileOwned(Player ownedPlayer) {
            if (closed) {
                return;
            }
            if (ownedPlayer.isRemoved()) {
                cleanup(playerId);
                return;
            }

            Optional<ScoreboardFrame> rendered = scoreboards.render(playerId);
            if (rendered.isEmpty()) {
                sessions.remove(playerId, this);
                closeOwned(ownedPlayer);
                return;
            }

            ScoreboardFrame frame = rendered.orElseThrow();
            if (sidebar == null) {
                sidebar = new Sidebar(frame.title());
                sidebar.addViewer(ownedPlayer);
            } else {
                sidebar.setTitle(frame.title());
                if (!sidebar.getViewers().contains(ownedPlayer)) {
                    sidebar.addViewer(ownedPlayer);
                }
            }
            writeLines(frame.lines());
        }

        private void writeLines(List<ScoreboardLine> lines) {
            int size = lines.size();
            for (ScoreboardLine line : lines) {
                String id = lineId(line.index());
                int score = size - line.index();
                if (sidebar.getLine(id) == null) {
                    sidebar.createLine(new Sidebar.ScoreboardLine(
                            id,
                            line.content(),
                            score,
                            Sidebar.NumberFormat.blank()
                    ));
                } else {
                    sidebar.updateLineContent(id, line.content());
                    sidebar.updateLineScore(id, score);
                    sidebar.updateLineNumberFormat(id, Sidebar.NumberFormat.blank());
                }
            }
            for (int index = size; index < renderedLineCount; index++) {
                sidebar.removeLine(lineId(index));
            }
            renderedLineCount = size;
        }

        private boolean closed() {
            return closed;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (player.isOnline()) {
                player.acquirable().sync(this::closeOwned);
            } else {
                sidebar = null;
                renderedLineCount = 0;
            }
        }

        private void closeOwned(Player ownedPlayer) {
            if (sidebar != null) {
                sidebar.removeViewer(ownedPlayer);
                for (int index = 0; index < renderedLineCount; index++) {
                    sidebar.removeLine(lineId(index));
                }
                sidebar = null;
            }
            renderedLineCount = 0;
            closed = true;
        }
    }
}
