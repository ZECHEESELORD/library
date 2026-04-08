# Migration Notes

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
- Audited Minestom runtime adapters now fail fast when a supposedly owned-thread future is still incomplete instead of blocking that thread with `.join()`.
