---
name: entity-house-layer
description: Author, review, document, or propose changes to the shared entity layer and House service-entity layer in this repo. Use when adding or changing generic entity API/core behavior, Paper or Minestom entity adapters, the Paper Citizens bridge, House service entities, NPC/service presentation, entity examples, entity capability docs, or entity-layer proposals so the work uses `EntitySpec`, `HouseServiceSpec`, typed capabilities, honest backend support, and mandatory House style anywhere NPCs are involved.
---

# Entity + House Layer

Use the entity stack as two layers: generic entities first, House service entities second. NPCs are not an escape hatch around the generic model. They are generic entities wrapped in the mandatory House presentation model.

## Apply the hard rules

- Use `EntitySpec` plus the platform `spawn(...)` path for generic entities.
- Use `HouseServiceSpec` plus `spawnService(...)` for NPCs, guides, vendors, bankers, menu-openers, and other interactive named service entities.
- Enforce House style anywhere NPCs are involved.
- Do not author raw hologram lines, arbitrary name stacks, or ad hoc prompt text as the normal path.
- Do not bypass the common API with raw Bukkit, Minestom, or Citizens calls unless you are inside the backend adapter that owns that host.
- Use `capability(...)` and branch on absence. Unsupported behavior must be absent, not faked.
- Do not bypass the structured `name` + `description` House inputs with alternate label stacks or backend-owned text shortcuts.
- Read `references/examples.md` before introducing a new caller pattern or approving a new entity proposal.
- Check `docs/entity-capability-matrix.md` when support claims matter.
- Check `docs/entity-manual-verification.md` when visual or runtime behavior matters.

## Choose the layer first

Use the generic entity layer for:
- animals, monsters, villagers, armor stands, display entities, and utility entities
- non-service named entities
- backend and capability work that is not about House presentation

Use the House service layer for:
- named interactive NPCs
- guides, bankers, vendors, blacksmiths, menu-openers, and other service entities
- any entity where the caller expects the fixed 3-line House presentation

Do not use the House service layer for:
- generic ambient mobs
- decorative displays with no service role
- utility entities whose behavior is not NPC or service facing

## Keep ownership in the right layer

- `entity-api` owns host-neutral types, entity specs, managed handles, interactions, and capabilities.
- `entity-core` owns shared validation, lifecycle guards, and owner-thread rules.
- `house-service-entity` owns `HouseServiceSpec`, validation, line ordering, and the wrapped service-entity surface.
- `entity-paper` and `entity-minestom` own native runtime mapping, native interactions, and honest capability exposure.
- `entity-paper-citizens` owns Paper player-like humanoid and skin bridging only.
- examples and docs own smoke harnesses, verification steps, and developer guidance only.

## Use the correct caller shape

Use generic spawn for generic entities.

```java
ManagedEntity villager = platform.spawn(world, EntitySpec.builder(EntityTypes.VILLAGER)
        .transform(...)
        .flags(CommonEntityFlags.builder()
                .customName(Component.text("Market Villager"))
                .customNameVisible(true)
                .build())
        .build());
```

Resultant behavior: a generic villager spawns with the configured flags. No House presentation stack is created.

Use House service spawn for NPC or service entities.

```java
HouseServiceEntity banker = platform.spawnService(world, HouseServiceSpec.builder(
                EntitySpec.builder(EntityTypes.VILLAGER)
                        .transform(...)
                        .build())
        .name("&bMeredith")
        .description("Banker")
        .clickHandler(context -> openBank(context.interactor()))
        .build());
```

Resultant behavior:
- line 1: colored NPC name, for example `Meredith`
- line 2: gray bracketed description, for example `[Banker]`
- line 3: fixed `CLICK`
- the base entity keeps a hidden dark-gray `[NPC] <uuid8>` diagnostic name
- callers do not control raw line ordering or free-form hologram text

Use capability lookup for specialized behavior.

```java
entity.capability(AiCapable.class).ifPresent(ai -> ai.ai(false));
```

Resultant behavior: AI is only toggled when that entity and backend actually expose `AiCapable`. Unsupported entities simply do not provide the capability.

## Respect backend truth

- Native Paper must reject `EntityTypes.PLAYER_LIKE_HUMANOID`. Use the Citizens bridge for that case.
- Citizens is a narrow bridge, not the foundation of the entity stack.
- Minestom stays native and explicit; do not mirror Bukkit assumptions into it.
- Do not claim support that the current backend cannot honestly provide. Use the support matrix and document degraded behavior explicitly.

## Review and proposal rules

For every entity-layer API change, design note, or proposal, include these sections:

- `Caller usage`
- `Layer ownership`
- `Resultant behavior`

Reject proposals that:
- describe internals without showing caller code
- move behavior into the wrong layer
- add fake universal methods instead of capabilities
- bypass House presentation for NPC or service entities
- rely on raw backend types in common modules

## Anti-patterns

- Do not build a giant entity interface with nullable methods for every family.
- Do not accept raw hologram line lists as the normal NPC or service authoring path.
- Do not leak Citizens types into common APIs.
- Do not use native Paper for `creative:player_like_humanoid`.
- Do not silently rewrite or reorder House presentation.
- Do not mutate live entities off the owner thread.
- Do not introduce alternate NPC style systems beside the House layer.
