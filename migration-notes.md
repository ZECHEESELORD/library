# Migration Notes

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
