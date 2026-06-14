package sh.harold.library.scoreboard.paper;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import sh.harold.library.scoreboard.ScoreboardFrame;
import sh.harold.library.scoreboard.ScoreboardLine;
import sh.harold.library.scoreboard.ScoreboardSection;
import sh.harold.library.scoreboard.ScoreboardService;
import sh.harold.library.scoreboard.ScoreboardSpec;
import sh.harold.library.scoreboard.TransientSectionSpec;
import sh.harold.library.scoreboard.core.StandardScoreboardService;
import sh.harold.library.tick.KeyedHandle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaperScoreboardPlatform implements Listener, AutoCloseable {
    private static final String OBJECTIVE_NAME = "creative_sb";

    private final JavaPlugin plugin;
    private final StandardScoreboardService scoreboards = new StandardScoreboardService();
    private final Map<UUID, PaperScoreboardSession> sessions = new ConcurrentHashMap<>();

    private BukkitTask tickTask;
    private boolean closed;

    public PaperScoreboardPlatform(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ScoreboardService service() {
        return scoreboards;
    }

    public void register(ScoreboardSpec spec) {
        scoreboards.register(spec);
    }

    public void unregister(Key key) {
        requirePrimaryThread("unregister Paper scoreboards");
        scoreboards.unregister(key);
        reconcileAll();
    }

    public void show(Player player, Key scoreboardKey) {
        requirePrimaryThread("show Paper scoreboards");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.show(target.getUniqueId(), Objects.requireNonNull(scoreboardKey, "scoreboardKey"));
        session(target).reconcile(target);
        ensureTicking();
    }

    public void hide(Player player) {
        requirePrimaryThread("hide Paper scoreboards");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.hide(target.getUniqueId());
        PaperScoreboardSession session = sessions.remove(target.getUniqueId());
        if (session != null) {
            session.close(target);
        }
        cancelTickingIfIdle();
    }

    public void clearViewer(Player player) {
        hide(player);
    }

    public void overrideTitle(Player player, Component title) {
        requirePrimaryThread("override Paper scoreboard titles");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.overrideTitle(target.getUniqueId(), Objects.requireNonNull(title, "title"));
        reconcile(target);
    }

    public void clearTitleOverride(Player player) {
        requirePrimaryThread("clear Paper scoreboard title overrides");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearTitleOverride(target.getUniqueId());
        reconcile(target);
    }

    public void overrideSection(Player player, String sectionId, ScoreboardSection replacement) {
        requirePrimaryThread("override Paper scoreboard sections");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.overrideSection(target.getUniqueId(), sectionId, replacement);
        reconcile(target);
    }

    public void clearSectionOverride(Player player, String sectionId) {
        requirePrimaryThread("clear Paper scoreboard section overrides");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearSectionOverride(target.getUniqueId(), sectionId);
        reconcile(target);
    }

    public void hideSection(Player player, String sectionId) {
        requirePrimaryThread("hide Paper scoreboard sections");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.hideSection(target.getUniqueId(), sectionId);
        reconcile(target);
    }

    public void showSection(Player player, String sectionId) {
        requirePrimaryThread("show Paper scoreboard sections");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.showSection(target.getUniqueId(), sectionId);
        reconcile(target);
    }

    public KeyedHandle pushTransient(Player player, TransientSectionSpec spec) {
        requirePrimaryThread("push Paper scoreboard transients");
        Player target = Objects.requireNonNull(player, "player");
        KeyedHandle handle = scoreboards.pushTransient(target.getUniqueId(), spec);
        reconcile(target);
        ensureTicking();
        return handle;
    }

    public void clearTransient(Player player, Key key) {
        requirePrimaryThread("clear Paper scoreboard transients");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearTransient(target.getUniqueId(), key);
        reconcile(target);
    }

    public void clearTransients(Player player) {
        requirePrimaryThread("clear Paper scoreboard transients");
        Player target = Objects.requireNonNull(player, "player");
        scoreboards.clearTransients(target.getUniqueId());
        reconcile(target);
    }

    @Override
    public void close() {
        requirePrimaryThread("close Paper scoreboard platform");
        if (closed) {
            return;
        }
        closed = true;
        HandlerList.unregisterAll(this);
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        List<PaperScoreboardSession> currentSessions = new ArrayList<>(sessions.values());
        sessions.clear();
        currentSessions.forEach(PaperScoreboardSession::close);
        scoreboards.close();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerKick(PlayerKickEvent event) {
        cleanup(event.getPlayer());
    }

    private PaperScoreboardSession session(Player player) {
        return sessions.compute(player.getUniqueId(), (ignored, existing) -> {
            if (existing == null || existing.closed()) {
                return new PaperScoreboardSession(player);
            }
            return existing;
        });
    }

    private void cleanup(Player player) {
        scoreboards.clearViewer(player.getUniqueId());
        PaperScoreboardSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.close();
        }
        cancelTickingIfIdle();
    }

    private void reconcile(Player player) {
        PaperScoreboardSession session = sessions.get(player.getUniqueId());
        if (session != null) {
            session.reconcile(player);
        }
        cancelTickingIfIdle();
    }

    private void reconcileAll() {
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                cleanupById(playerId);
            } else {
                reconcile(player);
            }
        }
        cancelTickingIfIdle();
    }

    private void cleanupById(UUID playerId) {
        scoreboards.clearViewer(playerId);
        PaperScoreboardSession session = sessions.remove(playerId);
        if (session != null) {
            session.close();
        }
    }

    private void ensureTicking() {
        if (tickTask == null && !sessions.isEmpty()) {
            tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
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

    private static void requirePrimaryThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(action + " must run on the Paper primary server thread");
        }
    }

    private static String entryId(int index) {
        return "clib_sb_" + index;
    }

    private final class PaperScoreboardSession {
        private final UUID playerId;
        private final Scoreboard previousScoreboard;

        private Scoreboard scoreboard;
        private Objective objective;
        private int renderedLineCount;
        private boolean closed;

        private PaperScoreboardSession(Player player) {
            this.playerId = player.getUniqueId();
            this.previousScoreboard = player.getScoreboard();
        }

        private void reconcile(Player player) {
            if (closed) {
                return;
            }
            Optional<ScoreboardFrame> rendered = scoreboards.render(playerId);
            if (rendered.isEmpty()) {
                sessions.remove(playerId, this);
                close(player);
                return;
            }
            ScoreboardFrame frame = rendered.orElseThrow();
            ensureObjective(player, frame);
            objective.displayName(frame.title());
            writeLines(frame.lines());
            if (player.getScoreboard() != scoreboard) {
                player.setScoreboard(scoreboard);
            }
        }

        private void ensureObjective(Player player, ScoreboardFrame frame) {
            if (objectiveUsable()) {
                return;
            }

            ScoreboardManager manager = Objects.requireNonNull(Bukkit.getScoreboardManager(), "scoreboard manager");
            scoreboard = manager.getNewScoreboard();
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, frame.title());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            player.setScoreboard(scoreboard);
        }

        private void writeLines(List<ScoreboardLine> lines) {
            int size = lines.size();
            for (ScoreboardLine line : lines) {
                String entry = entryId(line.index());
                Score score = objective.getScore(entry);
                score.setScore(size - line.index());
                score.customName(line.content());
                score.numberFormat(NumberFormat.blank());
            }
            for (int index = size; index < renderedLineCount; index++) {
                scoreboard.resetScores(entryId(index));
            }
            renderedLineCount = size;
        }

        private boolean closed() {
            return closed;
        }

        private void close() {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                close(player);
                return;
            }
            closed = true;
        }

        private void close(Player player) {
            if (closed) {
                return;
            }
            closed = true;
            if (player.getScoreboard() == scoreboard) {
                player.setScoreboard(previousScoreboard);
            }
            if (objective != null) {
                try {
                    objective.unregister();
                } catch (IllegalStateException ignored) {
                    // Already unregistered by the host.
                }
            }
            scoreboard = null;
            objective = null;
            renderedLineCount = 0;
        }

        private boolean objectiveUsable() {
            if (scoreboard == null || objective == null) {
                return false;
            }
            try {
                return objective.getScoreboard() == scoreboard;
            } catch (IllegalStateException ignored) {
                return false;
            }
        }
    }
}
