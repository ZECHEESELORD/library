package sh.harold.creative.library.entity.minestom;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityCreature;
import net.minestom.server.entity.EntityPose;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.VillagerProfession;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.metadata.AgeableMobMeta;
import net.minestom.server.entity.metadata.avatar.MannequinMeta;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.BlockDisplayMeta;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.entity.metadata.other.ArmorStandMeta;
import net.minestom.server.entity.metadata.villager.VillagerMeta;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.player.PlayerEntityInteractEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.player.ResolvableProfile;
import net.minestom.server.thread.AcquirableOwnershipException;
import sh.harold.creative.library.entity.BlockDescriptor;
import sh.harold.creative.library.entity.EntitySpec;
import sh.harold.creative.library.entity.EntityTransform;
import sh.harold.creative.library.entity.EntityTypeKey;
import sh.harold.creative.library.entity.EntityTypes;
import sh.harold.creative.library.entity.InteractionKind;
import sh.harold.creative.library.entity.InteractorRef;
import sh.harold.creative.library.entity.ItemDescriptor;
import sh.harold.creative.library.entity.ManagedEntity;
import sh.harold.creative.library.entity.SkinTexture;
import sh.harold.creative.library.entity.capability.AgeableCapable;
import sh.harold.creative.library.entity.capability.BlockDisplayCapable;
import sh.harold.creative.library.entity.capability.DisplayCapable;
import sh.harold.creative.library.entity.capability.Equipable;
import sh.harold.creative.library.entity.capability.ItemDisplayCapable;
import sh.harold.creative.library.entity.capability.LeashCapable;
import sh.harold.creative.library.entity.capability.PassengerCapable;
import sh.harold.creative.library.entity.capability.PoseCapable;
import sh.harold.creative.library.entity.capability.SkinCapable;
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
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class MinestomEntityPlatform implements AutoCloseable {

    private final Map<UUID, MinestomManagedEntity> entities = new ConcurrentHashMap<>();
    private final EventNode<Event> eventNode = EventNode.all("library-entity-platform");

    public MinestomEntityPlatform() {
        eventNode.addListener(PlayerEntityInteractEvent.class, this::onInteract);
        eventNode.addListener(EntityAttackEvent.class, this::onAttack);
        MinecraftServer.getGlobalEventHandler().addChild(eventNode);
    }

    public ManagedEntity spawn(Instance instance, EntitySpec spec) {
        EntitySpecValidator.validate(spec);
        Entity entity = createEntity(spec.type());
        entity.setInstance(instance, toPos(spec.transform())).join();

        MinestomManagedEntity managedEntity = new MinestomManagedEntity(entity, spec, () -> entities.remove(entity.getUuid()));
        entities.put(managedEntity.id(), managedEntity);
        return managedEntity;
    }

    public HouseServiceEntity spawnService(Instance instance, HouseServiceSpec serviceSpec) {
        HouseValidator.validate(serviceSpec);
        HousePresentation presentation = HousePresentationFactory.create(serviceSpec);
        ManagedEntity anchor = spawn(instance, serviceSpec.entitySpec());
        anchor.clearCustomName();
        anchor.customNameVisible(false);

        HousePresentationRenderer renderer = new MinestomHouseRenderer(((MinestomManagedEntity) anchor).entity, presentation);
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
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode);
        List<UUID> ids = new ArrayList<>(entities.keySet());
        for (UUID id : ids) {
            MinestomManagedEntity entity = entities.get(id);
            if (entity != null) {
                entity.despawn();
            }
        }
        entities.clear();
    }

    private void onInteract(PlayerEntityInteractEvent event) {
        MinestomManagedEntity entity = entities.get(event.getTarget().getUuid());
        if (entity != null) {
            InteractionKind kind = event.getHand() == PlayerHand.MAIN ? InteractionKind.PRIMARY : InteractionKind.SECONDARY;
            entity.handleInteraction(new InteractorRef(event.getPlayer().getUuid()), kind);
        }
    }

    private void onAttack(EntityAttackEvent event) {
        MinestomManagedEntity entity = entities.get(event.getTarget().getUuid());
        if (entity != null && event.getEntity() instanceof net.minestom.server.entity.Player player) {
            entity.handleInteraction(new InteractorRef(player.getUuid()), InteractionKind.ATTACK);
        }
    }

    private static Entity createEntity(EntityTypeKey type) {
        EntityType resolvedType = resolveEntityType(type);
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            return new LivingEntity(resolvedType);
        }
        return switch (type.family()) {
            case HUMANOID, MONSTER, ANIMAL, VILLAGER -> new EntityCreature(resolvedType);
            default -> new Entity(resolvedType);
        };
    }

    private static EntityType resolveEntityType(EntityTypeKey type) {
        if (EntityTypes.PLAYER_LIKE_HUMANOID.equals(type)) {
            return Objects.requireNonNull(EntityType.fromKey("minecraft:mannequin"), "Minestom mannequin type");
        }
        EntityType resolved = EntityType.fromKey(type.key());
        if (resolved == null) {
            throw new IllegalArgumentException("Unsupported Minestom entity type " + type.key());
        }
        return resolved;
    }

    private static Pos toPos(EntityTransform transform) {
        return new Pos(transform.x(), transform.y(), transform.z(), transform.yaw(), transform.pitch());
    }

    private static ItemStack toItemStack(ItemDescriptor descriptor) {
        Material material = Material.fromKey(descriptor.key());
        if (material == null) {
            throw new IllegalArgumentException("Unsupported item key " + descriptor.key());
        }
        return ItemStack.of(material, descriptor.amount());
    }

    private static Block toBlock(BlockDescriptor descriptor) {
        Block block = Block.fromKey(descriptor.key());
        if (block == null) {
            throw new IllegalArgumentException("Unsupported block key " + descriptor.key());
        }
        return block;
    }

    private static EquipmentSlot toMinestomSlot(sh.harold.creative.library.entity.EquipmentSlot slot) {
        return switch (slot) {
            case MAIN_HAND -> EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> EquipmentSlot.OFF_HAND;
            case FEET -> EquipmentSlot.BOOTS;
            case LEGS -> EquipmentSlot.LEGGINGS;
            case CHEST -> EquipmentSlot.CHESTPLATE;
            case HEAD -> EquipmentSlot.HELMET;
            case BODY -> EquipmentSlot.BODY;
        };
    }

    private static sh.harold.creative.library.entity.EntityPose fromMinestomPose(EntityPose pose) {
        return switch (pose) {
            case STANDING -> sh.harold.creative.library.entity.EntityPose.STANDING;
            case SNEAKING -> sh.harold.creative.library.entity.EntityPose.CROUCHING;
            case SLEEPING -> sh.harold.creative.library.entity.EntityPose.SLEEPING;
            case SITTING -> sh.harold.creative.library.entity.EntityPose.SITTING;
            case SWIMMING -> sh.harold.creative.library.entity.EntityPose.SWIMMING;
            case SPIN_ATTACK -> sh.harold.creative.library.entity.EntityPose.SPIN_ATTACK;
            default -> sh.harold.creative.library.entity.EntityPose.STANDING;
        };
    }

    private static EntityPose toMinestomPose(sh.harold.creative.library.entity.EntityPose pose) {
        return switch (pose) {
            case CROUCHING -> EntityPose.SNEAKING;
            case SLEEPING -> EntityPose.SLEEPING;
            case SITTING -> EntityPose.SITTING;
            case SWIMMING -> EntityPose.SWIMMING;
            case SPIN_ATTACK -> EntityPose.SPIN_ATTACK;
            case STANDING -> EntityPose.STANDING;
        };
    }

    private final class MinestomManagedEntity extends AbstractManagedEntity {
        private final Entity entity;
        private final Runnable onDespawn;
        private volatile SkinTexture appliedSkin;

        private MinestomManagedEntity(Entity entity, EntitySpec spec, Runnable onDespawn) {
            super(entity.getUuid(), spec);
            this.entity = entity;
            this.onDespawn = onDespawn;
            registerCapabilities();
            applyInitialState();
        }

        private void registerCapabilities() {
            registerCapability(PassengerCapable.class, new PassengerCapable() {
                @Override
                public List<UUID> passengers() {
                    return entity.getPassengers().stream().map(Entity::getUuid).toList();
                }

                @Override
                public boolean addPassenger(ManagedEntity other) {
                    if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.addPassenger(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean removePassenger(ManagedEntity other) {
                    if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.removePassenger(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }
            });

            registerCapability(LeashCapable.class, new LeashCapable() {
                @Override
                public java.util.Optional<UUID> leashHolder() {
                    Entity leashHolder = entity.getLeashHolder();
                    return leashHolder == null ? java.util.Optional.empty() : java.util.Optional.of(leashHolder.getUuid());
                }

                @Override
                    public boolean leashHolder(ManagedEntity other) {
                        MinestomManagedEntity.this.requireMutable();
                        if (other instanceof MinestomManagedEntity minestomManagedEntity) {
                        entity.setLeashHolder(minestomManagedEntity.entity);
                        return true;
                    }
                    return false;
                }

                @Override
                    public void clearLeash() {
                        MinestomManagedEntity.this.requireMutable();
                        entity.setLeashHolder(null);
                    }
            });

            registerCapability(PoseCapable.class, new PoseCapable() {
                @Override
                public sh.harold.creative.library.entity.EntityPose pose() {
                    return fromMinestomPose(entity.getPose());
                }

                @Override
                public void pose(sh.harold.creative.library.entity.EntityPose pose) {
                    MinestomManagedEntity.this.requireMutable();
                    entity.setPose(toMinestomPose(pose));
                }
            });

            if (entity instanceof LivingEntity livingEntity) {
                registerCapability(Equipable.class, new Equipable() {
                    @Override
                    public java.util.Optional<ItemDescriptor> equipment(sh.harold.creative.library.entity.EquipmentSlot slot) {
                        ItemStack stack = livingEntity.getEquipment(toMinestomSlot(slot));
                        if (stack == null || stack.isAir()) {
                            return java.util.Optional.empty();
                        }
                        return java.util.Optional.of(new ItemDescriptor(stack.material().key(), stack.amount()));
                    }

                    @Override
                    public void equipment(sh.harold.creative.library.entity.EquipmentSlot slot, ItemDescriptor item) {
                        MinestomManagedEntity.this.requireMutable();
                        livingEntity.setEquipment(toMinestomSlot(slot), toItemStack(item));
                    }

                    @Override
                    public void clearEquipment(sh.harold.creative.library.entity.EquipmentSlot slot) {
                        MinestomManagedEntity.this.requireMutable();
                        livingEntity.setEquipment(toMinestomSlot(slot), ItemStack.AIR);
                    }
                });
            }

            if (entity.getEntityMeta() instanceof AgeableMobMeta) {
                registerCapability(AgeableCapable.class, new AgeableCapable() {
                    @Override
                    public boolean adult() {
                        return !((AgeableMobMeta) entity.getEntityMeta()).isBaby();
                    }

                    @Override
                    public void adult(boolean adult) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AgeableMobMeta.class, meta -> meta.setBaby(!adult));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof VillagerMeta villagerMeta) {
                registerCapability(VillagerLikeCapable.class, new VillagerLikeCapable() {
                    @Override
                    public int level() {
                        return villagerMeta.getVillagerData().level().ordinal() + 1;
                    }

                    @Override
                    public void level(int level) {
                        MinestomManagedEntity.this.requireMutable();
                        VillagerMeta.Level[] levels = VillagerMeta.Level.values();
                        int index = Math.max(0, Math.min(level - 1, levels.length - 1));
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withLevel(levels[index]))
                        );
                    }

                    @Override
                    public java.util.Optional<Key> profession() {
                        return java.util.Optional.of(villagerMeta.getVillagerData().profession().key());
                    }

                    @Override
                    public void profession(Key profession) {
                        MinestomManagedEntity.this.requireMutable();
                        VillagerProfession villagerProfession = VillagerProfession.fromKey(profession);
                        if (villagerProfession == null) {
                            throw new IllegalArgumentException("Unknown villager profession " + profession);
                        }
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withProfession(villagerProfession))
                        );
                    }

                    @Override
                    public void clearProfession() {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(VillagerMeta.class, meta ->
                                meta.setVillagerData(meta.getVillagerData().withProfession(VillagerProfession.fromKey("minecraft:none")))
                        );
                    }
                });
            }

            if (entity.getEntityMeta() instanceof AbstractDisplayMeta displayMeta) {
                registerCapability(DisplayCapable.class, new DisplayCapable() {
                    @Override
                    public float width() {
                        return displayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return displayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(AbstractDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof TextDisplayMeta textDisplayMeta) {
                registerCapability(TextDisplayCapable.class, new TextDisplayCapable() {
                    @Override
                    public Component text() {
                        return textDisplayMeta.getText();
                    }

                    @Override
                    public void text(Component text) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(text));
                    }

                    @Override
                    public float width() {
                        return textDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return textDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(TextDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof ItemDisplayMeta itemDisplayMeta) {
                registerCapability(ItemDisplayCapable.class, new ItemDisplayCapable() {
                    @Override
                    public ItemDescriptor item() {
                        ItemStack stack = itemDisplayMeta.getItemStack();
                        return new ItemDescriptor(stack.material().key(), stack.amount());
                    }

                    @Override
                    public void item(ItemDescriptor item) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(toItemStack(item)));
                    }

                    @Override
                    public float width() {
                        return itemDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return itemDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof BlockDisplayMeta blockDisplayMeta) {
                registerCapability(BlockDisplayCapable.class, new BlockDisplayCapable() {
                    @Override
                    public BlockDescriptor block() {
                        return new BlockDescriptor(blockDisplayMeta.getBlockStateId().key());
                    }

                    @Override
                    public void block(BlockDescriptor block) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setBlockState(toBlock(block)));
                    }

                    @Override
                    public float width() {
                        return blockDisplayMeta.getWidth();
                    }

                    @Override
                    public void width(float width) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setWidth(width));
                    }

                    @Override
                    public float height() {
                        return blockDisplayMeta.getHeight();
                    }

                    @Override
                    public void height(float height) {
                        MinestomManagedEntity.this.requireMutable();
                        entity.editEntityMeta(BlockDisplayMeta.class, meta -> meta.setHeight(height));
                    }
                });
            }

            if (entity.getEntityMeta() instanceof MannequinMeta mannequinMeta) {
                registerCapability(SkinCapable.class, new SkinCapable() {
                    @Override
                    public java.util.Optional<SkinTexture> skin() {
                        return java.util.Optional.ofNullable(appliedSkin);
                    }

                    @Override
                    public void skin(SkinTexture skinTexture) {
                        MinestomManagedEntity.this.requireMutable();
                        appliedSkin = skinTexture;
                        entity.editEntityMeta(MannequinMeta.class, meta ->
                                meta.setProfile(new ResolvableProfile(new PlayerSkin(skinTexture.texture(), skinTexture.signature())))
                        );
                    }

                    @Override
                    public void clearSkin() {
                        MinestomManagedEntity.this.requireMutable();
                        appliedSkin = null;
                        entity.editEntityMeta(MannequinMeta.class, meta -> meta.setProfile(ResolvableProfile.EMPTY));
                    }
                });
            }
        }

        @Override
        protected void assertOwnerThread() {
            try {
                entity.acquirable().assertOwnership();
            } catch (AcquirableOwnershipException exception) {
                throw new IllegalStateException(
                        "Mutating Minestom entity " + id() + " requires the owning tick thread or an acquired entity context",
                        exception
                );
            }
        }

        @Override
        protected void doTeleport(EntityTransform transform) {
            entity.teleport(toPos(transform)).join();
        }

        @Override
        protected void doCustomName(Component customName) {
            entity.setCustomName(customName);
        }

        @Override
        protected void doClearCustomName() {
            entity.setCustomName(Component.empty());
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
            entity.setNoGravity(!gravity);
        }

        @Override
        protected void doInvulnerable(boolean invulnerable) {
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setInvulnerable(invulnerable);
            } else if (invulnerable) {
                throw new UnsupportedOperationException("This Minestom entity type is not invulnerable-capable");
            }
        }

        @Override
        protected void doDespawn() {
            entity.remove();
            onDespawn.run();
        }
    }

    private static final class MinestomHouseRenderer implements HousePresentationRenderer {
        private static final double LINE_SPACING = 0.3;

        private final List<Entity> displays = new ArrayList<>();

        private MinestomHouseRenderer(Entity anchor, HousePresentation presentation) {
            List<Component> lines = presentation.lines();
            Entity vehicle = anchor;
            for (int index = lines.size() - 1; index >= 0; index--) {
                double offset = (lines.size() - 1 - index) * LINE_SPACING;
                Entity display = spawnLine(anchor.getInstance(), anchor.getPosition(), offset, lines.get(index));
                vehicle.addPassenger(display);
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
            for (Entity display : displays) {
                display.remove();
            }
            displays.clear();
        }

        private static Entity spawnLine(Instance instance, Pos anchorPosition, double offset, Component text) {
            Entity entity = createEntity(EntityTypes.ARMOR_STAND);
            entity.editEntityMeta(ArmorStandMeta.class, meta -> {
                meta.setSmall(true);
                meta.setMarker(true);
                meta.setHasNoBasePlate(true);
            });
            entity.setCustomName(text);
            entity.setCustomNameVisible(true);
            entity.setInvisible(true);
            entity.setSilent(true);
            entity.setNoGravity(true);
            entity.setInstance(instance, anchorPosition.add(0.0, offset, 0.0)).join();
            return entity;
        }
    }
}
