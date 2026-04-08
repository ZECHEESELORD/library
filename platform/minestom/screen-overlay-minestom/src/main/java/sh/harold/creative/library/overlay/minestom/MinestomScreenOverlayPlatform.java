package sh.harold.creative.library.overlay.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.instance.RemoveEntityFromInstanceEvent;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerRespawnEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.Scheduler;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import sh.harold.creative.library.overlay.ScreenOverlayHandle;
import sh.harold.creative.library.overlay.ScreenOverlayRequest;
import sh.harold.creative.library.overlay.core.OverlayFace;
import sh.harold.creative.library.overlay.core.ScreenOverlayComposite;
import sh.harold.creative.library.overlay.core.ScreenOverlayShellGeometry;
import sh.harold.creative.library.overlay.core.StandardScreenOverlayController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class MinestomScreenOverlayPlatform implements AutoCloseable {
    private static final Component BLANK_TEXT = Component.text(" ");

    private final EventNode<Event> parentNode;
    private final EventNode<Event> runtimeNode;
    private final Scheduler scheduler;
    private final Map<UUID, MinestomOverlaySession> sessions = new ConcurrentHashMap<>();

    public MinestomScreenOverlayPlatform() {
        this(MinecraftServer.getGlobalEventHandler(), MinecraftServer.getSchedulerManager());
    }

    public MinestomScreenOverlayPlatform(EventNode<Event> parentNode) {
        this(parentNode, MinecraftServer.getSchedulerManager());
    }

    public MinestomScreenOverlayPlatform(EventNode<Event> parentNode, Scheduler scheduler) {
        this.parentNode = Objects.requireNonNull(parentNode, "parentNode");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.runtimeNode = EventNode.all("screen-overlay-runtime-" + UUID.randomUUID());
        runtimeNode.addListener(PlayerDisconnectEvent.class, event -> cleanup(event.getPlayer().getUuid()));
        runtimeNode.addListener(PlayerDeathEvent.class, event -> cleanup(event.getPlayer().getUuid()));
        runtimeNode.addListener(PlayerRespawnEvent.class, event -> cleanup(event.getPlayer().getUuid()));
        runtimeNode.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                cleanup(event.getPlayer().getUuid());
            }
        });
        runtimeNode.addListener(RemoveEntityFromInstanceEvent.class, event -> {
            if (event.getEntity() instanceof Player player) {
                cleanup(player.getUuid());
            }
        });
        this.parentNode.addChild(runtimeNode);
    }

    public ScreenOverlayHandle show(Player player, ScreenOverlayRequest request) {
        Player target = Objects.requireNonNull(player, "player");
        MinestomOverlaySession session = sessions.compute(target.getUuid(), (ignored, existing) ->
                existing == null || existing.closed() ? new MinestomOverlaySession(target) : existing
        );
        return session.show(Objects.requireNonNull(request, "request"));
    }

    public void clear(Player player, Key key) {
        MinestomOverlaySession session = sessions.get(Objects.requireNonNull(player, "player").getUuid());
        if (session != null) {
            session.clear(Objects.requireNonNull(key, "key"));
        }
    }

    public void clearAll(Player player) {
        MinestomOverlaySession session = sessions.get(Objects.requireNonNull(player, "player").getUuid());
        if (session != null) {
            session.clearAll();
        }
    }

    @Override
    public void close() {
        parentNode.removeChild(runtimeNode);
        List<MinestomOverlaySession> currentSessions = new ArrayList<>(sessions.values());
        sessions.clear();
        currentSessions.forEach(MinestomOverlaySession::close);
    }

    private void cleanup(UUID playerId) {
        MinestomOverlaySession session = sessions.remove(playerId);
        if (session != null) {
            session.close();
        }
    }

    private static ScreenOverlayHandle inactiveHandle(Key key) {
        return new ScreenOverlayHandle() {
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

    private final class MinestomOverlaySession {
        private final Player player;
        private final UUID playerId;
        private final StandardScreenOverlayController controller = new StandardScreenOverlayController();

        private MinestomOverlayShell shell;
        private Task tickTask;
        private volatile boolean closed;

        private MinestomOverlaySession(Player player) {
            this.player = player;
            this.playerId = player.getUuid();
        }

        private ScreenOverlayHandle show(ScreenOverlayRequest request) {
            ensureTicking();
            AtomicReference<ScreenOverlayHandle> handle = new AtomicReference<>(inactiveHandle(request.key()));
            player.acquirable().sync(ownedPlayer -> {
                if (closed) {
                    return;
                }
                handle.set(controller.show(request));
                reconcileOwned(ownedPlayer);
            });
            return handle.get();
        }

        private void clear(Key key) {
            player.acquirable().sync(ownedPlayer -> {
                if (closed) {
                    return;
                }
                controller.clear(key);
                reconcileOwned(ownedPlayer);
            });
        }

        private void clearAll() {
            player.acquirable().sync(ownedPlayer -> {
                if (closed) {
                    return;
                }
                controller.clearAll();
                reconcileOwned(ownedPlayer);
            });
        }

        private boolean closed() {
            return closed;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            controller.close();
            Task task = tickTask;
            tickTask = null;
            if (task != null) {
                task.cancel();
            }
            MinestomOverlayShell existingShell = shell;
            shell = null;
            if (existingShell != null) {
                existingShell.closeAsync();
            }
        }

        private void closeOwned() {
            if (closed) {
                return;
            }
            closed = true;
            controller.close();
            Task task = tickTask;
            tickTask = null;
            if (task != null) {
                task.cancel();
            }
            if (shell != null) {
                shell.closeDirect();
                shell = null;
            }
        }

        private void ensureTicking() {
            if (tickTask == null) {
                tickTask = scheduler.scheduleTask(
                        this::tick,
                        TaskSchedule.tick(1),
                        TaskSchedule.tick(1)
                );
            }
        }

        private void tick() {
            if (closed) {
                return;
            }
            if (!player.isOnline()) {
                sessions.remove(playerId, this);
                close();
                return;
            }
            player.acquirable().sync(this::tickOwned);
        }

        private void tickOwned(Player ownedPlayer) {
            if (closed) {
                return;
            }
            if (ownedPlayer.isRemoved() || ownedPlayer.isDead()) {
                sessions.remove(playerId, this);
                closeOwned();
                return;
            }
            controller.advance();
            reconcileOwned(ownedPlayer);
        }

        private void reconcileOwned(Player ownedPlayer) {
            if (closed) {
                return;
            }
            if (!controller.hasActiveOverlays()) {
                sessions.remove(playerId, this);
                closeOwned();
                return;
            }

            if (shell == null || shell.instance() != ownedPlayer.getInstance()) {
                if (shell != null) {
                    shell.closeDirect();
                }
                shell = new MinestomOverlayShell(ownedPlayer);
            }
            shell.updateOwned(ownedPlayer, controller.composite());
        }
    }

    private static final class MinestomOverlayShell {
        private final List<MinestomOverlayFace> faces = new ArrayList<>(ScreenOverlayShellGeometry.faces().size());

        private MinestomOverlayShell(Player owner) {
            Instance instance = Objects.requireNonNull(owner.getInstance(), "player instance");
            for (OverlayFace face : ScreenOverlayShellGeometry.faces()) {
                Entity entity = new Entity(EntityType.TEXT_DISPLAY);
                entity.setAutoViewable(false);
                entity.setSilent(true);
                entity.setNoGravity(true);
                entity.setHasPhysics(false);
                entity.setBoundingBox(0.001, 0.001, 0.001);
                entity.editEntityMeta(TextDisplayMeta.class, meta -> configure(meta, face));
                MinestomFutureGuard.requireCompleted(entity.setInstance(instance, facePosition(owner, face)), "screen overlay shell spawn");
                entity.addViewer(owner);
                faces.add(new MinestomOverlayFace(face, entity));
            }
        }

        private Instance instance() {
            return faces.getFirst().entity().getInstance();
        }

        private void updateOwned(Player owner, ScreenOverlayComposite composite) {
            int backgroundColor = composite.argb();
            byte textOpacity = (byte) composite.alphaByte();
            for (MinestomOverlayFace face : faces) {
                Entity entity = face.entity();
                MinestomFutureGuard.requireCompleted(entity.teleport(facePosition(owner, face.face())), "screen overlay shell teleport");
                entity.editEntityMeta(TextDisplayMeta.class, meta -> {
                    meta.setBackgroundColor(backgroundColor);
                    meta.setTextOpacity(textOpacity);
                });
            }
        }

        private void closeAsync() {
            if (faces.isEmpty()) {
                return;
            }
            Entity anchor = faces.getFirst().entity();
            if (anchor.isRemoved()) {
                closeDirect();
                return;
            }
            anchor.acquirable().sync(ignored -> closeDirect());
        }

        private void closeDirect() {
            for (MinestomOverlayFace face : faces) {
                if (!face.entity().isRemoved()) {
                    face.entity().remove();
                }
            }
            faces.clear();
        }

        private static void configure(TextDisplayMeta meta, OverlayFace face) {
            meta.setText(BLANK_TEXT);
            meta.setLineWidth(ScreenOverlayShellGeometry.BLANK_TEXT_LINE_WIDTH);
            meta.setAlignment(TextDisplayMeta.Alignment.CENTER);
            meta.setUseDefaultBackground(false);
            meta.setShadow(false);
            meta.setSeeThrough(true);
            meta.setBackgroundColor(0);
            meta.setTextOpacity((byte) 0);
            meta.setTransformationInterpolationStartDelta(0);
            meta.setTransformationInterpolationDuration(1);
            meta.setPosRotInterpolationDuration(1);
            meta.setBrightness(15, 15);
            meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.FIXED);
            meta.setViewRange(1.0f);
            meta.setWidth(ScreenOverlayShellGeometry.faceWidth(face));
            meta.setHeight(ScreenOverlayShellGeometry.faceHeight(face));
            meta.setShadowRadius(0.0f);
            meta.setShadowStrength(0.0f);

            Vector3f translation = ScreenOverlayShellGeometry.localFaceTranslation(face);
            Vector3f scale = ScreenOverlayShellGeometry.localFaceScale(face);
            Quaternionf rotation = ScreenOverlayShellGeometry.localFaceRotation(face);
            meta.setTranslation(new Vec(translation.x, translation.y, translation.z));
            meta.setScale(new Vec(scale.x, scale.y, scale.z));
            meta.setLeftRotation(new float[]{rotation.x, rotation.y, rotation.z, rotation.w});
            meta.setRightRotation(new float[]{0.0f, 0.0f, 0.0f, 1.0f});
        }

        private static Pos facePosition(Player owner, OverlayFace face) {
            Pos base = owner.getPosition();
            Vector3f offset = ScreenOverlayShellGeometry.faceCenterOffset(face);
            return new Pos(base.x() + offset.x, base.y() + offset.y, base.z() + offset.z, 0.0f, 0.0f);
        }
    }

    private record MinestomOverlayFace(OverlayFace face, Entity entity) {
    }
}
