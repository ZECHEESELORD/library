package sh.harold.creative.library.entity.paper;

import io.papermc.paper.entity.Leashable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import sh.harold.creative.library.entity.BlockDescriptor;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypeKey;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.EquipmentSlot;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.ItemDescriptor;
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.capability.AiCapable;
import sh.harold.creative.library.entity.capability.AgeableCapable;
import sh.harold.creative.library.entity.capability.BlockDisplayCapable;
import sh.harold.creative.library.entity.capability.CollidableCapable;
import sh.harold.creative.library.entity.capability.DisplayCapable;
import sh.harold.creative.library.entity.capability.Equipable;
import sh.harold.creative.library.entity.capability.ItemDisplayCapable;
import sh.harold.creative.library.entity.capability.LeashCapable;
import sh.harold.creative.library.entity.capability.PassengerCapable;
import sh.harold.creative.library.entity.capability.PersistenceCapable;
import sh.harold.creative.library.entity.capability.TextDisplayCapable;
import sh.harold.creative.library.entity.capability.VillagerLikeCapable;
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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class PaperEntityPlatform implements Listener, AutoCloseable {

    private final Plugin plugin;
    private final Map<UUID, PaperManagedEntity> entities = new ConcurrentHashMap<>();

    public PaperEntityPlatform(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ManagedEntity spawn(World world, EntitySpec spec) {
        requirePrimaryThread("spawn Paper entities");
        EntitySpecValidator.validate(spec);
        EntityType type = toBukkitType(spec.type());
        Location location = toLocation(world, spec.transform());
        Entity entity = world.spawnEntity(location, type);
        PaperManagedEntity managedEntity = new PaperManagedEntity(entity, spec, () -> entities.remove(entity.getUniqueId()));
        entities.put(managedEntity.id(), managedEntity);
        return managedEntity;
    }

    public HouseServiceEntity spawnService(World world, HouseServiceSpec serviceSpec) {
        requirePrimaryThread("spawn Paper service entities");
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        ManagedEntity anchor = spawn(world, serviceSpec.entitySpec());
        anchor.clearCustomName();
        anchor.customNameVisible(false);

        HousePresentationRenderer renderer = new PaperHouseRenderer(((PaperManagedEntity) anchor).entity, presentation);
        StandardHouseServiceEntity serviceEntity = new StandardHouseServiceEntity(anchor, presentation, renderer);

        AtomicReference<HouseServiceEntity> reference = new AtomicReference<>(serviceEntity);
        serviceSpec.entitySpec().interactionHandler().ifPresent(anchor::interactionHandler);
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
        requirePrimaryThread("close Paper entity platform");
        HandlerList.unregisterAll(this);
        List<UUID> ids = new ArrayList<>(entities.keySet());
        for (UUID id : ids) {
            PaperManagedEntity entity = entities.get(id);
            if (entity != null) {
                entity.despawn();
            }
        }
        entities.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getRightClicked().getUniqueId());
        if (entity != null) {
            entity.handleInteraction(new InteractorRef(event.getPlayer().getUniqueId()), InteractionKind.SECONDARY);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        PaperManagedEntity entity = entities.get(event.getEntity().getUniqueId());
        if (entity != null && event.getDamager() instanceof Player player) {
            entity.handleInteraction(new InteractorRef(player.getUniqueId()), InteractionKind.ATTACK);
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

    private static EntityType toBukkitType(EntityTypeKey type) {
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            throw new IllegalArgumentException("creative:player_like_humanoid requires the Citizens bridge on Paper");
        }
        String name = type.key().value().toUpperCase(Locale.ROOT).replace('-', '_');
        return EntityType.valueOf(name);
    }

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        Material material = Material.matchMaterial(descriptor.key().asString());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return new ItemStack(material, descriptor.amount());
    }

    private static BlockData toBlockData(BlockDescriptor descriptor) {
        Material material = Material.matchMaterial(descriptor.key().asString());
        if (material == null || !material.isBlock()) {
            throw new IllegalArgumentException("Unsupported block key " + descriptor.key());
        }
        return Bukkit.createBlockData(material);
    }

    private static org.bukkit.inventory.EquipmentSlot toBukkitSlot(EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> org.bukkit.inventory.EquipmentSlot.HAND;
            case OFF_HAND -> org.bukkit.inventory.EquipmentSlot.OFF_HAND;
            case FEET -> org.bukkit.inventory.EquipmentSlot.FEET;
            case LEGS -> org.bukkit.inventory.EquipmentSlot.LEGS;
            case CHEST -> org.bukkit.inventory.EquipmentSlot.CHEST;
            case HEAD -> org.bukkit.inventory.EquipmentSlot.HEAD;
            case BODY -> org.bukkit.inventory.EquipmentSlot.BODY;
        };
    }

    private final class PaperManagedEntity extends AbstractManagedEntity {
        private final Entity entity;
        private final Runnable onDespawn;

        private PaperManagedEntity(Entity entity, EntitySpec spec, Runnable onDespawn) {
            super(entity.getUniqueId(), spec);
            this.entity = entity;
            this.onDespawn = onDespawn;
            registerCapabilities();
            applyInitialState();
        }

        private void registerCapabilities() {
            registerCapability(PassengerCapable.class, new PassengerCapable() {
                @Override
                public List<UUID> passengers() {
                    return entity.getPassengers().stream().map(Entity::getUniqueId).toList();
                }

                @Override
                public boolean addPassenger(ManagedEntity other) {
                    if (other instanceof PaperManagedEntity paperManagedEntity) {
                        return entity.addPassenger(paperManagedEntity.entity);
                    }
                    return false;
                }

                @Override
                public boolean removePassenger(ManagedEntity other) {
                    if (other instanceof PaperManagedEntity paperManagedEntity) {
                        return entity.removePassenger(paperManagedEntity.entity);
                    }
                    return false;
                }
            });

            if (entity instanceof Mob mob) {
                registerCapability(AiCapable.class, new AiCapable() {
                    @Override
                    public boolean aiEnabled() {
                        return mob.hasAI();
                    }

                    @Override
                    public void aiEnabled(boolean enabled) {
                        PaperManagedEntity.this.requireMutable();
                        mob.setAI(enabled);
                    }
                });
            }

            if (entity instanceof Ageable ageable) {
                registerCapability(AgeableCapable.class, new AgeableCapable() {
                    @Override
                    public boolean adult() {
                        return ageable.isAdult();
                    }

                    @Override
                    public void adult(boolean adult) {
                        PaperManagedEntity.this.requireMutable();
                        if (adult) {
                            ageable.setAdult();
                        } else {
                            ageable.setBaby();
                        }
                    }
                });
            }

            if (entity instanceof LivingEntity livingEntity) {
                registerCapability(Equipable.class, new Equipable() {
                    @Override
                    public java.util.Optional<ItemDescriptor> equipment(EquipmentSlot slot) {
                        ItemStack stack = livingEntity.getEquipment().getItem(toBukkitSlot(slot));
                        if (stack == null || stack.getType() == Material.AIR) {
                            return java.util.Optional.empty();
                        }
                        return java.util.Optional.of(new ItemDescriptor(stack.getType().key(), stack.getAmount()));
                    }

                    @Override
                    public void equipment(EquipmentSlot slot, ItemDescriptor item) {
                        PaperManagedEntity.this.requireMutable();
                        livingEntity.getEquipment().setItem(toBukkitSlot(slot), toItemStack(item));
                    }

                    @Override
                    public void clearEquipment(EquipmentSlot slot) {
                        PaperManagedEntity.this.requireMutable();
                        livingEntity.getEquipment().setItem(toBukkitSlot(slot), null);
                    }
                });

                registerCapability(PersistenceCapable.class, new PersistenceCapable() {
                    @Override
                    public boolean persistent() {
                        return entity.isPersistent();
                    }

                    @Override
                    public void persistent(boolean persistent) {
                        PaperManagedEntity.this.requireMutable();
                        entity.setPersistent(persistent);
                    }
                });
            }

            if (entity instanceof LivingEntity collidableEntity) {
                registerCapability(CollidableCapable.class, new CollidableCapable() {
                    @Override
                    public boolean collidable() {
                        return collidableEntity.isCollidable();
                    }

                    @Override
                    public void collidable(boolean collidable) {
                        PaperManagedEntity.this.requireMutable();
                        collidableEntity.setCollidable(collidable);
                    }
                });
            }

            if (entity instanceof Leashable leashable) {
                registerCapability(LeashCapable.class, new LeashCapable() {
                    @Override
                    public java.util.Optional<UUID> leashHolder() {
                        Entity leashHolder = leashable.getLeashHolder();
                        return leashHolder == null ? java.util.Optional.empty() : java.util.Optional.of(leashHolder.getUniqueId());
                    }

                    @Override
                    public boolean leashHolder(ManagedEntity other) {
                        PaperManagedEntity.this.requireMutable();
                        if (other instanceof PaperManagedEntity paperManagedEntity) {
                            return leashable.setLeashHolder(paperManagedEntity.entity);
                        }
                        return false;
                    }

                    @Override
                    public void clearLeash() {
                        PaperManagedEntity.this.requireMutable();
                        leashable.setLeashHolder(null);
                    }
                });
            }

            if (entity instanceof Villager villager) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        return villager.getVillagerLevel();
                    }

                    @Override
                    public void level(int level) {
                        PaperManagedEntity.this.requireMutable();
                        villager.setVillagerLevel(level);
                    }

                    @Override
                    public java.util.Optional<net.kyori.adventure.key.Key> profession() {
                        return java.util.Optional.of(villager.getProfession().key());
                    }

                    @Override
                    public void profession(net.kyori.adventure.key.Key profession) {
                        PaperManagedEntity.this.requireMutable();
                        villager.setProfession(Villager.Profession.valueOf(profession.value().toUpperCase(Locale.ROOT).replace('-', '_')));
                    }

                    @Override
                    public void clearProfession() {
                        PaperManagedEntity.this.requireMutable();
                        villager.setProfession(Villager.Profession.NONE);
                    }
                });
            } else if (entity instanceof AbstractVillager abstractVillager) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        return 0;
                    }

                    @Override
                    public void level(int level) {
                        throw new UnsupportedOperationException("This villager-like entity has no level");
                    }

                    @Override
                    public java.util.Optional<net.kyori.adventure.key.Key> profession() {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public void profession(net.kyori.adventure.key.Key profession) {
                        throw new UnsupportedOperationException("This villager-like entity has no profession");
                    }

                    @Override
                    public void clearProfession() {
                    }
                });
            }

            if (entity instanceof Display display) {
                registerCapability(DisplayCapable.class, new DisplayCapable() {
                    @Override
                    public float width() {
                        return display.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        display.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        return display.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        display.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof TextDisplay textDisplay) {
                registerCapability(TextDisplayCapable.class, new TextDisplayCapable() {
                    @Override
                    public Component text() {
                        return textDisplay.text();
                    }

                    @Override
                    public void text(Component text) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.text(text);
                    }

                    @Override
                    public float width() {
                        return textDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        return textDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        textDisplay.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof ItemDisplay itemDisplay) {
                registerCapability(ItemDisplayCapable.class, new ItemDisplayCapable() {
                    @Override
                    public ItemDescriptor item() {
                        ItemStack stack = itemDisplay.getItemStack();
                        return new ItemDescriptor(stack.getType().key(), stack.getAmount());
                    }

                    @Override
                    public void item(ItemDescriptor item) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setItemStack(toItemStack(item));
                    }

                    @Override
                    public float width() {
                        return itemDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        return itemDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        itemDisplay.setDisplayHeight(height);
                    }
                });
            }

            if (entity instanceof BlockDisplay blockDisplay) {
                registerCapability(BlockDisplayCapable.class, new BlockDisplayCapable() {
                    @Override
                    public BlockDescriptor block() {
                        return new BlockDescriptor(blockDisplay.getBlock().getMaterial().key());
                    }

                    @Override
                    public void block(BlockDescriptor block) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setBlock(toBlockData(block));
                    }

                    @Override
                    public float width() {
                        return blockDisplay.getDisplayWidth();
                    }

                    @Override
                    public void width(float width) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setDisplayWidth(width);
                    }

                    @Override
                    public float height() {
                        return blockDisplay.getDisplayHeight();
                    }

                    @Override
                    public void height(float height) {
                        PaperManagedEntity.this.requireMutable();
                        blockDisplay.setDisplayHeight(height);
                    }
                });
            }
        }

        @Override
        protected void assertOwnerThread() {
            requirePrimaryThread("Mutating Paper entity " + id());
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
            entity.teleport(toLocation(entity.getWorld(), transform));
        }

        @Override
        protected void doCustomName(Component customName) {
            entity.customName(customName);
        }

        @Override
        protected void doClearCustomName() {
            entity.customName(null);
        }

        @Override
        protected void doCustomNameVisible(boolean visible) {
            entity.setCustomNameVisible(visible);
        }

        @Override
        protected void doGlowing(boolean glowing) {
            entity.setGlowing(glowing);
        }

        @Override
        protected void doSilent(boolean silent) {
            entity.setSilent(silent);
        }

        @Override
        protected void doGravity(boolean gravity) {
            entity.setGravity(gravity);
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
            entity.setInvulnerable(invulnerable);
        }

        @Override
        protected void doDespawn() {
            entity.remove();
            onDespawn.run();
        }
    }

    private static final class PaperHouseRenderer implements HousePresentationRenderer {
        private static final double LINE_SPACING = 0.3;

        private final List<ArmorStand> displays = new ArrayList<>();

        private PaperHouseRenderer(Entity anchor, HousePresentation presentation) {
            List<Component> lines = presentation.lines();
            Entity vehicle = anchor;
            for (int index = lines.size() - 1; index >= 0; index--) {
                double offset = (lines.size() - 1 - index) * LINE_SPACING;
                ArmorStand display = spawnLine(anchor.getWorld(), anchor.getLocation(), offset, lines.get(index));
                if (!vehicle.addPassenger(display)) {
                    display.remove();
                    throw new IllegalStateException("Failed to mount House line armor stand to " + vehicle.getUniqueId());
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
