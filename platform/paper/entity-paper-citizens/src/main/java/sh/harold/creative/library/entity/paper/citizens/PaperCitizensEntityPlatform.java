package sh.harold.creative.library.entity.paper.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.SkinTexture;
import sh.harold.creative.library.entity.capability.SkinCapable;
import sh.harold.creative.library.entity.core.AbstractManagedEntity;
import sh.harold.creative.library.entity.core.EntitySpecValidator;
import sh.harold.creative.library.entity.house.HousePresentation;
import sh.harold.creative.library.entity.house.HousePresentationFactory;
import sh.harold.creative.library.entity.house.HousePresentationRenderer;
import sh.harold.creative.library.entity.house.HouseServiceClickContext;
import sh.harold.creative.library.entity.house.HouseServiceEntity;
import sh.harold.creative.library.entity.house.HouseServiceSpec;
import sh.harold.creative.library.entity.house.HouseValidator;
import sh.harold.creative.library.entity.house.StandardHouseServiceEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class PaperCitizensEntityPlatform implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Map<UUID, CitizensManagedEntity> entities = new ConcurrentHashMap<>();

    public PaperCitizensEntityPlatform(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ManagedEntity spawn(World world, EntitySpec spec) {
        requirePrimaryThread("spawn Citizens-backed entities");
        EntitySpecValidator.validate(spec);
        if (!EntityTypes.PLAYER_LIKE_HUMANOID.equals(spec.type())) {
            throw new IllegalArgumentException("Citizens bridge only supports creative:player_like_humanoid");
        }

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Service Entity");
        npc.spawn(toLocation(world, spec.transform()));

        CitizensManagedEntity managedEntity = new CitizensManagedEntity(npc, spec, () -> entities.remove(npc.getUniqueId()));
        entities.put(managedEntity.id(), managedEntity);
        return managedEntity;
    }

    public HouseServiceEntity spawnService(World world, HouseServiceSpec serviceSpec) {
        requirePrimaryThread("spawn Citizens-backed service entities");
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        ManagedEntity anchor = spawn(world, serviceSpec.entitySpec());
        anchor.clearCustomName();
        anchor.customNameVisible(false);

        HousePresentationRenderer renderer = new CitizensHouseRenderer(((CitizensManagedEntity) anchor).currentEntity(), presentation);
        StandardHouseServiceEntity serviceEntity = new StandardHouseServiceEntity(anchor, presentation, renderer);

        AtomicReference<HouseServiceEntity> reference = new AtomicReference<>(serviceEntity);
        if (serviceSpec.clickHandler().isPresent() || serviceSpec.entitySpec().interactionHandler().isPresent()) {
            anchor.interactionHandler(context -> {
                serviceSpec.entitySpec().interactionHandler().ifPresent(handler -> handler.onInteract(context));
                serviceSpec.clickHandler().ifPresent(handler -> handler.onClick(
                        new HouseServiceClickContext(reference.get(), context.interactor(), context.kind())
                ));
            });
        }
        return serviceEntity;
    }

    @Override
    public void close() {
        requirePrimaryThread("close Citizens entity platform");
        HandlerList.unregisterAll(this);
        List<UUID> ids = new ArrayList<>(entities.keySet());
        for (UUID id : ids) {
            CitizensManagedEntity entity = entities.get(id);
            if (entity != null) {
                entity.despawn();
            }
        }
        entities.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(NPCRightClickEvent event) {
        CitizensManagedEntity entity = entities.get(event.getNPC().getUniqueId());
        if (entity != null) {
            entity.handleInteraction(new InteractorRef(event.getClicker().getUniqueId()), InteractionKind.SECONDARY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeftClick(NPCLeftClickEvent event) {
        CitizensManagedEntity entity = entities.get(event.getNPC().getUniqueId());
        if (entity != null) {
            entity.handleInteraction(new InteractorRef(event.getClicker().getUniqueId()), InteractionKind.ATTACK);
        }
    }

    private static Location toLocation(World world, EntityTransform transform) {
        return new Location(world, transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch());
    }

    private static void requirePrimaryThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(action + " must run on the Paper primary server thread");
        }
    }

    private final class CitizensManagedEntity extends AbstractManagedEntity {
        private final NPC npc;
        private final Runnable onDespawn;
        private final SkinTrait skinTrait;

        private CitizensManagedEntity(NPC npc, EntitySpec spec, Runnable onDespawn) {
            super(npc.getUniqueId(), spec);
            this.npc = npc;
            this.onDespawn = onDespawn;
            SkinTrait existingTrait = npc.getTraitNullable(SkinTrait.class);
            if (existingTrait == null) {
                npc.addTrait(SkinTrait.class);
                existingTrait = npc.getTraitNullable(SkinTrait.class);
            }
            this.skinTrait = Objects.requireNonNull(existingTrait, "skinTrait");
            registerCapability(SkinCapable.class, new SkinCapable() {
                @Override
                public java.util.Optional<SkinTexture> skin() {
                    if (skinTrait.getTexture() == null) {
                        return java.util.Optional.empty();
                    }
                    return java.util.Optional.of(new SkinTexture(skinTrait.getTexture(), Objects.toString(skinTrait.getSignature(), "")));
                }

                @Override
                public void skin(SkinTexture skinTexture) {
                    CitizensManagedEntity.this.requireMutable();
                    skinTrait.setSkinPersistent(id().toString(), skinTexture.signature(), skinTexture.texture());
                }

                @Override
                public void clearSkin() {
                    CitizensManagedEntity.this.requireMutable();
                    skinTrait.clearTexture();
                }
            });
            applyInitialState();
        }

        @Override
        protected void assertOwnerThread() {
            requirePrimaryThread("Mutating Citizens entity " + id());
        }

        private Entity currentEntity() {
            return Objects.requireNonNull(npc.getEntity(), "npc entity");
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
            currentEntity().teleport(toLocation(currentEntity().getWorld(), transform));
        }

        @Override
        protected void doCustomName(Component customName) {
            currentEntity().customName(customName);
        }

        @Override
        protected void doClearCustomName() {
            currentEntity().customName(null);
        }

        @Override
        protected void doCustomNameVisible(boolean visible) {
            currentEntity().setCustomNameVisible(visible);
            npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, visible);
        }

        @Override
        protected void doGlowing(boolean glowing) {
            currentEntity().setGlowing(glowing);
        }

        @Override
        protected void doSilent(boolean silent) {
            currentEntity().setSilent(silent);
        }

        @Override
        protected void doGravity(boolean gravity) {
            currentEntity().setGravity(gravity);
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
            currentEntity().setInvulnerable(invulnerable);
        }

        @Override
        protected void doDespawn() {
            npc.despawn();
            npc.destroy();
            onDespawn.run();
        }
    }

    private static final class CitizensHouseRenderer implements HousePresentationRenderer {
        private static final double LINE_SPACING = 0.3;

        private final List<ArmorStand> displays = new ArrayList<>();

        private CitizensHouseRenderer(Entity anchor, HousePresentation presentation) {
            List<Component> lines = presentation.lines();
            Entity vehicle = anchor;
            for (int index = lines.size() - 1; index >= 0; index--) {
                double offset = (lines.size() - 1 - index) * LINE_SPACING;
                ArmorStand display = spawnLine(anchor.getWorld(), anchor.getLocation(), offset, lines.get(index));
                if (!vehicle.addPassenger(display)) {
                    display.remove();
                    throw new IllegalStateException("Failed to mount Citizens House line armor stand to " + vehicle.getUniqueId());
                }
                displays.add(display);
                vehicle = display;
            }
        }

        @Override
        public void teleport(EntityTransform transform) {
            // Mounted passenger stacks follow the anchor entity automatically.
        }

        @Override
        public void close() {
            for (ArmorStand display : displays) {
                display.remove();
            }
            displays.clear();
        }

        private static ArmorStand spawnLine(World world, Location anchorLocation, double offset, Component text) {
            return world.spawn(
                    anchorLocation.clone().add(0.0, offset, 0.0),
                    ArmorStand.class,
                    display -> {
                        display.customName(text);
                        display.setCustomNameVisible(true);
                        display.setVisible(false);
                        display.setMarker(true);
                        display.setSmall(true);
                        display.setBasePlate(false);
                        display.setGravity(false);
                        display.setInvulnerable(true);
                        display.setSilent(true);
                        display.setCollidable(false);
                        display.setPersistent(false);
                        display.setCanTick(false);
                    }
            );
        }
    }
}
