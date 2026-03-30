# Entity + House Layer Examples

These examples show approved caller usage of the current entity stack. Resultant behavior describes the visible or runtime outcome the caller should expect.

Examples use Paper snippets for brevity. Minestom uses the same common contracts through `MinestomEntityPlatform` unless a note says otherwise.

## Choose the caller surface

| Use this | When | Avoid when |
| --- | --- | --- |
| `EntitySpec` + `spawn(...)` | generic mobs, animals, villagers, displays, utilities | named interactive NPC or service entities |
| `HouseServiceSpec` + `spawnService(...)` | guides, bankers, vendors, menu-openers, other NPC or service entities | generic ambient or decorative entities |
| `capability(...)` | family or backend specific behavior | pretending every entity supports every toggle |

## Generic entity

```java
ManagedEntity villager = platform.spawn(world, EntitySpec.builder(EntityTypes.VILLAGER)
        .transform(new EntityTransform(x, y, z, yaw, pitch))
        .flags(CommonEntityFlags.builder()
                .customName(Component.text("Market Villager"))
                .customNameVisible(true)
                .glowing(true)
                .build())
        .interactionHandler(context -> logger.info("Villager " + context.kind()))
        .build());
```

Resultant behavior:
- a generic villager spawns
- visible name: `Market Villager`
- glowing applies through the base toggle
- interaction callback fires through the backend's supported interaction mapping
- no House presentation stack is created

## House service entity

```java
HouseServiceEntity banker = platform.spawnService(world, HouseServiceSpec.builder(
                EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(new EntityTransform(x, y, z, yaw, pitch))
                        .flags(CommonEntityFlags.builder().gravity(false).build())
                        .build())
        .name("&bMeredith")
        .description("Banker")
        .clickHandler(context -> openBank(context.interactor()))
        .build());
```

Resultant behavior:
- the anchor entity is still a generic villager
- visible line 1 = colored NPC name, for example `Meredith`
- visible line 2 = gray bracketed description, for example `[Banker]`
- visible line 3 = `CLICK`
- hidden anchor label = dark-gray `[NPC] <uuid8>`
- the backend owns the rendered sidecars; callers do not pass raw hologram lines

## Capability branching

```java
entity.capability(AiCapable.class).ifPresent(ai -> ai.ai(false));
entity.capability(SkinCapable.class)
        .ifPresentOrElse(
                skin -> skin.skin(texture),
                () -> logger.info("Skin unsupported here"));
```

Resultant behavior:
- supported capabilities mutate the entity
- unsupported capabilities are simply absent
- callers do not branch on backend type or call fake no-op methods

## Native Paper humanoid vs Citizens bridge

Incorrect:

```java
PaperEntityPlatform paper = new PaperEntityPlatform(plugin);
paper.spawn(world, EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
        .transform(new EntityTransform(x, y, z, yaw, pitch))
        .build());
```

Resultant behavior:
- explicit failure because native Paper does not support `creative:player_like_humanoid`

Correct:

```java
PaperCitizensEntityPlatform citizens = new PaperCitizensEntityPlatform(plugin);
HouseServiceEntity guide = citizens.spawnService(world, HouseServiceSpec.builder(
                EntitySpec.builder(EntityTypes.PLAYER_LIKE_HUMANOID)
                        .transform(new EntityTransform(x, y, z, yaw, pitch))
                        .build())
        .name("&aGideon")
        .description("Guide")
        .build());
```

Resultant behavior:
- a Citizens-backed humanoid NPC appears
- visible line 1 = colored NPC name, for example `Gideon`
- visible line 2 = gray bracketed description, for example `[Guide]`
- visible line 3 = `CLICK`
- hidden anchor label = dark-gray `[NPC] <uuid8>`
- Citizens stays inside the bridge module

## Owner-thread mutations

Correct:

```java
Bukkit.getScheduler().runTask(plugin, () -> entity.teleport(new EntityTransform(x, y, z, yaw, pitch)));
```

Incorrect:

```java
CompletableFuture.runAsync(() -> entity.teleport(new EntityTransform(x, y, z, yaw, pitch)));
```

Resultant behavior:
- owner-thread mutation succeeds
- obvious off-thread mutation fails fast instead of mutating unpredictably

## Correct vs incorrect patterns

| Correct | Incorrect |
| --- | --- |
| generic entity -> `spawn(...)` | NPC or service entity built from ad hoc entity + raw hologram lines |
| `HouseServiceSpec.name/description` | caller passes arbitrary free-form line arrays |
| `capability(...)` and absence checks | mega-interface or fake no-op methods |
| `entity-paper-citizens` for Paper humanoids | native Paper tries to spawn `creative:player_like_humanoid` |
| backend-specific types stay in adapters | common modules import Citizens, Bukkit, or Minestom runtime types |
