package sh.harold.creative.library.entity.paper.citizens;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCLeftClickEvent;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
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
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        ManagedEntity anchor = spawn(world, serviceSpec.entitySpec());
        anchor.customName(presentation.name());
        anchor.customNameVisible(true);

        HousePresentationRenderer renderer = new CitizensHouseRenderer(world, anchor.transform(), presentation);
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
                    requireSpawned();
                    skinTrait.setSkinPersistent(id().toString(), skinTexture.signature(), skinTexture.texture());
                }

                @Override
                public void clearSkin() {
                    requireSpawned();
                    skinTrait.clearTexture();
                }
            });
            applyInitialState();
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
        private final List<TextDisplay> displays = new ArrayList<>();

        private CitizensHouseRenderer(World world, EntityTransform anchorTransform, HousePresentation presentation) {
            displays.add(spawnLine(world, anchorTransform, 0.35, presentation.role()));
            displays.add(spawnLine(world, anchorTransform, 0.10, presentation.prompt()));
        }

        @Override
        public void teleport(EntityTransform transform) {
            if (displays.size() != 2) {
                return;
            }
            displays.get(0).teleport(new Location(displays.get(0).getWorld(), transform.x(), transform.y() + 0.35, transform.z(), transform.yaw(), transform.pitch()));
            displays.get(1).teleport(new Location(displays.get(1).getWorld(), transform.x(), transform.y() + 0.10, transform.z(), transform.yaw(), transform.pitch()));
        }

        @Override
        public void close() {
            for (TextDisplay display : displays) {
                display.remove();
            }
            displays.clear();
        }

        private static TextDisplay spawnLine(World world, EntityTransform transform, double offset, Component text) {
            return world.spawn(
                    new Location(world, transform.x(), transform.y() + offset, transform.z(), transform.yaw(), transform.pitch()),
                    TextDisplay.class,
                    display -> {
                        display.text(text);
                        display.setGravity(false);
                        display.setInvulnerable(true);
                        display.setPersistent(false);
                    }
            );
        }
    }
}
