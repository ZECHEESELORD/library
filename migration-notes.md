# Migration Notes

## v12

- Paper consumers on the latest lane move to Paper/ShreddedPaper `26.3` (`api-version: '26.3'`). Servers still on `26.2` should stay on v11 or use the retained `1.21.11` lane.
- `entity-paper` now requires the PacketEvents `2.14.0` server plugin. `PaperEntityPlatform` rejects `2.13.0` and any protocol other than `V_26_3`.
- Minestom adapters are `compileOnlyApi` on Minestom `2026.10.07-26.2` (Minecraft `26.2`). Minestom has no `26.3` build yet, so `26.3` clients cannot join Minestom servers. It brings Adventure `5.2.0`; `ClickEvent.value()` is replaced by a typed `payload()`, `Block.isAir()` is now `Block.air()` (likewise solid and liquid), and building an `ItemStack` needs bound registries (`MinecraftServer.init()`), which matters for consumer unit tests.
- `message-velocity` and `data-velocity` are compiled for Java 25 against Velocity API `4.3.1`. Consumers need a Java 25 toolchain and a Velocity 4.x proxy. The library API did not change.
- Latest-lane Fabric mods now require Fabric Loader `0.20.1` or newer and `fabric-api` `0.166.2` or newer. The retained `1.21.11` lane keeps its v11 runtime requirements (Loader `0.18.6` or newer, any `fabric-api`); it is now built against Fabric API `0.141.6+1.21.11`.
- Shared dependencies that reach consumers moved: the common `-api` modules expose `adventure-api` `4.26.1` (was `4.17.0`), and because it imports `adventure-bom` `4.26.1`, Gradle also aligns sibling Adventure modules (for example the copy a Fabric mod bundles, or the Adventure behind `adventure-platform-bukkit` in `message-paper-1_8_8`) up to `4.26.1`. `data-mongodb` exposes `mongodb-driver-sync` `5.15.0` (was `5.1.4`), and `data-core` ships `caffeine` `3.3.1` (was `3.1.8`) at runtime. Consumers that pin or shade older versions of these should re-check them. The library API did not change.
- `message-paper-1_8_8` is now compiled for Java 21 instead of Java 25, so Spigot 1.8.8 servers and Gradle consumers on Java 21 can load and resolve it. It still needs a Java 21 runtime.
- The `minestom-example` harness port can be overridden with `-Dminestom.example.port=<port>`, either on `./gradlew runMinestomExample` or when launching the main class directly; the default is still `25565`.

## v9

- The NPC behavior expansion is a source-breaking v9 re-release. Paper now uses native mannequins and requires PacketEvents `2.13.0`; the Citizens adapter, repository, dependency, settings, and examples have been removed completely.
- Entity interactions now use `EntityInteractionAction` (`USE` or `ATTACK`), an optional `InteractionHand`, and an `EntityInteractionResult` (`PASS` or `CONSUME`). Use `EntityInteractionHandler.observing(...)` when migrating a former void observer lambda.
- `PLAYER_LIKE_HUMANOID` exposes `HumanoidBehaviorCapable` on Paper and Minestom. Behavior remains opt-in: a mannequin without an `NpcBehaviorProfile` is inert, and explicit behavior commands fail fast until configured.
- Paper entity creation, service creation, teleport, and cleanup have asynchronous Folia-safe entry points. Synchronous entry points are owner-region fast paths and fail clearly from a foreign lane.

For a former void interaction observer, return an explicit result or use the migration helper:

```java
.interactionHandler(EntityInteractionHandler.observing(
        context -> audit(context.interactor(), context.action(), context.hand()),
        EntityInteractionResult.PASS
))
```

`USE` always carries `MAIN_HAND` or `OFF_HAND`; `ATTACK` carries no hand. Application handlers still receive both actions after behavior observes them. Safe native mannequins consume both actions and suppress vanilla damage and knockback, while generic entities remain `PASS` by default.

Replace any dependency or construction of the removed Paper Citizens adapter with `entity-paper` and `new PaperEntityPlatform(plugin)`. Install PacketEvents `2.13.0` as a server plugin and declare it as a required dependency. On Paper/Folia, migrate lifecycle calls to `spawnAsync`, `spawnServiceAsync`, `teleportAsync`, and `closeAsync` unless the caller is already on the owning region lane.
- YAML deleted-document revisions are now durable across restart. `WriteCondition.revision(...)` can safely guard delete-then-recreate flows after the owner restarts.
- Mongo initialization is now lazy on first async operation instead of forcing `ping` and revision-index setup during owner startup.
- Mongo insert retries now treat only duplicate-key insert failures as retryable conflicts. Other write failures surface immediately.
- Owned YAML and Mongo executors now close with a bounded graceful drain before falling back to forced shutdown.
- Velocity data owner now closes its `DataApi` during proxy shutdown.
- Minestom shared-owner registration now uses a JVM-global registry instead of a classloader-local static map.
- Compiled menu validation now resolves only the initial frame during build. Other frames validate lazily on first access.
- Paged and tabbed compiled menus now keep lazy frame-id sets instead of materializing every frame id up front.
- Reactive placement compilation is now bounded and keyed by visual state plus prompt labels, so visually identical items can reuse compiled presentation without pinning stale interactions.
- Paper and Minestom menu renderers now use bounded visual-state caches instead of unbounded `MenuSlot` caches.
- `stateFactory(...)` is now the only stateful reactive initializer; the shared-instance `state(...)` entry point has been removed so stateful menus can create fresh state for each session.
- Reactive reducers now return one of `ReactiveMenuResult.unchanged()`, `effect(...)`, or `update(...)`, with at most one control effect. The former `stay(...)`, `of(...)`, and multi-effect result shapes have been removed.
- Reactive canvas item movement now uses fixed `custodyTarget(...)` declarations plus a `custodyPolicy(...)`. Platform adapters own exact native-stack custody, accept only whole-stack movement, reject occupied destinations, return outside clicks to the origin, and report committed or rejected outcomes to reducers.
- Menu navigation, prompts, close, disconnect, and shutdown attempt to settle held custody before changing lifecycle state when the host still permits owner-thread work. If native ownership cannot be proven, adapters retire or tombstone their claim instead of crediting another copy; this no-dupe-first behavior may lose an item. Hard-crash exactly-once recovery remains outside the non-journaled runtime contract. Folia hot-disable or reload with active Paper menu sessions is unsupported.
- Lifecycle calls made reentrantly from a reducer, renderer, custody policy, or lifecycle callback are fenced and deferred to the next owner tick. Stale callback results are discarded before native inventory mutation, effects, or rendering; callbacks should still remain synchronous and side-effect-free.
- Back history is now a bounded menu-to-menu breadcrumb trail. Frame, page, and tab changes no longer create Back entries.
- Unchanged reactive results no longer trigger native rendering. Work for changing views still depends on their tick interval and rendered content. High-frequency reactive menus should keep titles stable because Paper and Fabric rebuild and reopen the native inventory when the title changes.
- Audited Minestom runtime adapters now fail fast when a supposedly owned-thread future is still incomplete instead of blocking that thread with `.join()`.
