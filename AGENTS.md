# AGENTS.md

## Scope

These instructions apply to all design, extraction, and implementation work for the new shared cross platform library.

Current target hosts:
- Paper
- Minestom
- Velocity

Current approved feature families:
- entity layer
- data API
- standardized message API
- state machine API
- menu API

This repository is not the place to dump every useful class from older projects.
Do not casually extract:
- scoreboards
- NPC frameworks
- item engines
- stat systems
- party or friends systems
- punishment systems
- proxy routing stacks
- gameplay specific minigame logic
- anything whose real shape is “one project’s internals with the nouns sanded off”

If local skills exist for menus, commands, messaging, or the entity layer, treat them as implementation standards.
In particular:
- the repo-local `entity-house-layer` skill is authoritative for entity API usage, House service-entity usage, NPC/service patterns, entity examples, entity docs, and entity-layer proposal structure
- the repo-local `menu-authoring` skill is authoritative for menu UX, geometry selection, flat menu DSL usage, menu card copy, and `MenuButton` versus `MenuDisplayItem`
- the Paper command skill is authoritative for any Paper command examples or bootstrap commands
- the `player-facing-messages` skill is authoritative for non-menu player-facing messaging patterns in samples, adapters, and player-visible copy

If there is a conflict:
- this file is the source of truth for architecture, boundaries, and extraction policy
- the `entity-house-layer` skill is the source of truth for entity-layer API usage, House NPC/service authoring, and the required shape of entity-layer proposals
- the `menu-authoring` skill is the source of truth for menu taxonomy, house-style usage, menu card authoring, and runtime menu UX details
- the command skill is the source of truth for Paper command registration style
- the `player-facing-messages` skill is the source of truth for non-menu player-facing message usage

---

## Objective

Build a small, explicit, reusable foundation.

The library should be:
- cross platform where that is honest
- platform specific where that is necessary
- boring in the good way: predictable, inspectable, and hard to misuse
- strict about boundaries
- minimal by default

This project should feel like a toolkit, not a museum of old codebases.

---

## The Only Honest Architecture

Every feature must be placed along these axes.

### 1. Domain contract

This is the public shape of a feature.
It defines the nouns, lifecycle, and invariants.

A domain contract:
- must not depend on host types like `Player`, `JavaPlugin`, `ProxyServer`, or Minestom server classes
- may depend on cross platform libraries that are genuinely shared, such as Adventure components
- should be small enough to read top to bottom without losing the plot

Examples:
- `data-api`
- `message-api`
- `state-machine-api`
- `menu-api`

### 2. Shared engine

This is host agnostic behavior that implements or supports a domain contract.
It is reusable logic, not lifecycle wiring.

A shared engine:
- may implement contracts from the API layer
- may own pure Java state and algorithms
- must not open inventories, schedule platform tasks, or call platform send methods

Examples:
- `message-core`
- `state-machine-core`
- `menu-core`
- `data-memory`

### 3. Platform adapter

This translates a shared contract into a specific host.

A platform adapter:
- may depend on exactly one host family unless it is a bootstrap module
- owns host type conversions and lifecycle glue
- must stay thin
- must not redefine the domain model

Examples:
- `message-paper`
- `message-minestom`
- `message-velocity`
- `menu-paper`
- `menu-minestom`

### 4. Bootstrap or example

This is composition code, not a public library surface.
It exists to prove wiring and provide runnable examples.

A bootstrap module:
- may depend on several modules from one host family
- must not become the real home of business logic
- should stay small and disposable

Examples:
- `paper-example`
- `minestom-example`
- `velocity-example`

Do not skip these boundaries.
Do not solve discomfort with a new “common everything” module.
That pattern always looks cheaper than it is.

---

## Capability Matrix

Not every feature belongs on every host.
Model what exists. Do not invent symmetry.

### Data API

Supported on:
- Paper
- Minestom
- Velocity

Shape:
- common contract
- host agnostic reference implementation
- optional backend modules later

Notes:
- data is not a menu, not a plugin lifecycle, and not a Paper service by default
- the contract should work without knowing which host is calling it

### Standardized Message API

Supported on:
- Paper
- Minestom
- Velocity

Shape:
- common contract
- shared resolver and builder logic
- per platform sender adapters

Notes:
- use Adventure as the common text currency
- host modules adapt native senders or audiences into the common messaging surface

### State Machine API

Supported on:
- Paper
- Minestom
- Velocity

Shape:
- common contract
- shared deterministic engine
- optional platform scheduling adapters outside the core

Notes:
- the public contract is reducer shaped: synchronous dispatch in, typed result out
- the core owns serial event processing, lifecycle ordering, and keyed timers
- hosts may provide scheduler implementations, but they do not own the machine model

### Menu API

Supported on:
- Paper
- Minestom

Not supported on:
- Velocity

Shape:
- one public menu API
- one shared menu core
- two backends: Paper and Minestom

Notes:
- do not create a fake Velocity menu module
- if a menu feature cannot be expressed cleanly on both Paper and Minestom, it does not belong in the common menu API
- host only extras belong in backend specific extension packages or wait until proven necessary

### Entity Layer

Supported on:
- Paper
- Minestom

Not supported on:
- Velocity as an entity runtime

Shape:
- one public generic entity API
- one shared entity core
- one shared House service-entity layer on top of the generic entity layer
- native Paper and Minestom backends
- optional Paper Citizens bridge for player-like humanoid entities only

Notes:
- generic entity first, NPC second
- typed capabilities instead of a universal god-interface
- House style is mandatory anywhere NPCs or interactive service entities are involved
- do not create a fake Velocity entity runtime module
- Citizens is a Paper bridge, not the global abstraction
- any entity-layer API or spec proposal must include caller usage, layer ownership, and resultant behavior

---

## Current Module Plan

The approved first cut is this.

```text
common/
  entity-api/
  entity-core/
  house-service-entity/
  data-api/
  data-memory/
  message-api/
  message-core/
  state-machine-api/
  state-machine-core/
  menu-api/
  menu-core/

platform/
  paper/
    entity-paper/
    entity-paper-citizens/
    message-paper/
    menu-paper/
    paper-entity-example/
    paper-example/
  minestom/
    entity-minestom/
    message-minestom/
    menu-minestom/
    minestom-entity-example/
    minestom-example/
  velocity/
    message-velocity/
    velocity-example/
```

### Why this split exists

- no `base` module is scaffolded in v1; add it later only if at least two approved feature families share a real cross cutting primitive
- `data-api` is the contract
- `data-memory` is the small reference implementation used by tests and examples
- `message-api` defines the public messaging surface
- `message-core` resolves templates, styles, tags, and fallback behavior
- `state-machine-api` defines reducer contracts, dispatch reports, and timer vocabulary
- `state-machine-core` provides the definition DSL, serial runtime, and timer machinery
- `menu-api` defines the platform neutral menu contract
- `menu-core` owns layout, slot mapping, page math, tab state, and render model
- `entity-api` defines the host-neutral generic entity surface
- `entity-core` owns shared validation, lifecycle guards, and capability support
- `house-service-entity` owns the mandatory structured House presentation for service entities, with a required name line, fixed `CLICK` line, and an optional descriptor line for richer roles
- `entity-paper` and `entity-minestom` are native host adapters
- `entity-paper-citizens` is a narrow Paper bridge for player-like humanoid entities only
- `message-paper`, `message-minestom`, `message-velocity` are sender adapters only
- `menu-paper` and `menu-minestom` translate the shared menu model into native inventory behavior
- example modules prove wiring and keep the integration honest

### What is intentionally absent

Not in v1:
- scoreboard modules
- ad hoc NPC frameworks outside the entity layer
- cooldown runtime services
- cross plugin runtime authority modules
- database driver zoo
- proxy orchestration layers
- gameplay bundles

Those may come later, but they do not get to squat in v1 on vibes alone.

---

## Dependency Rules

All dependencies must point down the abstraction ladder.

Allowed:
- platform modules depend on API and core modules
- core modules depend on API modules
- examples depend on platform modules and common modules

Forbidden:
- common modules depending on any platform module
- one platform family depending on another platform family
- Velocity modules depending on menu modules
- API modules depending on implementation modules
- bootstrap modules becoming the only place a feature actually works

A safe mental model:
- contracts know definitions
- engines know behavior
- adapters know hosts
- examples know composition

---

## How New Boundaries Are Decided

Before creating a new module, answer these questions in order.

1. Is this a new domain, or just a helper class?
2. Can the public contract be explained without naming a platform?
3. Does the shared behavior justify a core module, or is the API enough for now?
4. Does at least one real host need an adapter today?
5. Is the dependency boundary real, or are you splitting files because it feels architectural?

### Create a new API module only when:
- the feature is stable enough to deserve a contract
- the contract is host neutral
- at least two consumers or two platform families can realistically use it

### Create a new core module only when:
- there is nontrivial host agnostic behavior to share
- keeping that logic in adapters would duplicate real algorithms or state handling

### Create a new backend module only when:
- a host truly supports the capability
- the host mapping is different enough to deserve its own adapter
- the module is more than a thin bag of one line wrappers

### Do not create a module when:
- the feature still belongs to one application
- the shape is mostly unknown
- only one class would live there
- the “benefit” is future proofing theater

Start at the lowest honest layer.
Promote later only when reuse proves it.

---

## Feature Specific Boundary Rules

## Entity Layer

The entity stack is a first-class library feature in this repo.
When adding, reviewing, or proposing entity-layer or NPC-related work, use the repo-local `entity-house-layer` skill.

### Entity layer rules

- the base entity surface is generic first; House service entities are a second layer on top
- use `EntitySpec` plus the platform `spawn(...)` path for generic entities
- use `HouseServiceSpec` plus `spawnService(...)` for NPCs, guides, vendors, bankers, menu-openers, and other interactive named service entities
- House style is mandatory anywhere NPCs are involved
- callers must provide structured House fields such as name and optional description; do not make raw hologram lines the normal path
- entity-family-specific behavior belongs behind typed capabilities; unsupported capability behavior must be absent, not faked
- `entity-api` must stay host-neutral
- `entity-core` may own shared validation, lifecycle, and owner-thread guards, but not host lifecycle glue
- `house-service-entity` owns House validation, presentation ordering, and service wrapping
- `entity-paper` and `entity-minestom` own native runtime mapping and click routing
- `entity-paper-citizens` only owns Paper player-like humanoid and skin bridging; it must not leak Citizens types into common modules
- native Paper must reject `creative:player_like_humanoid`; do not hide that behind a soft fallback
- Minestom entity design must stay native and explicit, not Bukkit-shaped
- live entity operations use sync owned-thread semantics; do not hide that behind async redesigns or silent scheduling
- any entity-layer API or spec proposal must explicitly include:
  - caller usage
  - layer ownership
  - resultant behavior

### Entity module boundaries

Belongs in `entity-api`:
- open keyed entity types and families
- `EntitySpec`
- `ManagedEntity`
- common flags and transforms
- interaction contracts
- capability interfaces

Belongs in `entity-core`:
- shared validation
- lifecycle guards
- capability registration helpers
- owner-thread guard helpers

Belongs in `house-service-entity`:
- `HouseServiceSpec`
- `HousePresentation`
- `HouseValidator`
- `HouseServiceEntity`
- the mandatory structured House presentation model: colored name, optional gray bracketed descriptor, and fixed `CLICK`

Belongs in platform entity adapters:
- native spawning and despawn
- host interaction mapping
- honest capability exposure
- backend-owned House line renderers
- native lifecycle cleanup

Belongs in examples and docs:
- smoke harnesses
- support matrix documentation
- manual verification checklists

Not allowed:
- raw Citizens types in common modules
- a fake universal entity god-interface
- raw hologram line lists as the normal NPC/service authoring path
- backend adapters silently rewriting or reordering House presentation
- a Velocity entity runtime module

## Data API

The new data surface should be based on both uploaded codebases, but it should not blindly merge them.

Carry forward from `buh`:
- `DocumentKey`
- `DocumentSnapshot`
- `DocumentStore`
- `DocumentCollection`
- `DataApi`
- asynchronous collection and document operations
- a clean reference implementation pattern

Carry forward from `fulcrum`:
- `DocumentPatch`
- path based updates
- optional query and transaction extension points
- backend abstraction discipline

### Data API rules

- the base API is document oriented, not ORM flavored
- the contract is asynchronous first
- path based updates are part of the contract
- backend drivers do not belong in `data-api`
- ledgers, metrics, and specialty repositories do not belong in the base API unless they become clearly reusable across projects
- query support may start as an extension package or optional capability; it does not need to infect every first pass implementation

### Data module boundaries

Belongs in `data-api`:
- keys
- snapshots
- document contract
- collection contract
- patch contract
- backend SPI
- optional query SPI

Belongs in `data-memory`:
- in memory backend
- reference collection and document implementations
- tests

Does not belong in v1:
- MySQL, Mongo, Nitrite, Postgres, Redis, or other backend specific modules unless the repo truly needs them now
- project specific ledgers
- player repositories with baked in game assumptions

## Standardized Message API

The new message surface should keep the tiny entrypoint feel from `buh` and the resolver backed design from `fulcrum`.

### Message API rules

- Adventure `Component` is the shared text currency
- severity and tags belong in the common API
- the API may expose string identifiers and literal fallbacks
- template resolution belongs in `message-core`
- sending belongs in platform adapters
- examples and integrations should use the common facade, not raw host send calls
- any player-facing communication must go through the message API
- do not use raw `sendMessage`, raw Adventure components, MiniMessage strings, or legacy color-code strings for player-visible text outside the message library or renderers
- when adding or changing player-visible text, use the `player-facing-messages` skill
- if the current messaging surface is missing something, extend the shared message API instead of bypassing it
- treat legacy raw `Component` menu title or lore surfaces as debt; do not add new player-visible copy there without first extending the shared messaging integration

Belongs in `message-api`:
- `Message`
- `MessageBuilder`
- `MessageStyle` or severity type
- message tags
- resolver contract
- audience bridge contract if needed

Belongs in `message-core`:
- default resolver
- identifier fallback logic
- template formatting
- style helpers

Belongs in platform adapters:
- Paper sender bridges
- Minestom sender bridges
- Velocity sender bridges

Not allowed in the common API:
- `Player`
- `CommandSender`
- Velocity proxy types
- Minestom audience wrappers tied to the server runtime

## State Machine API

This should be extracted from the Fulcrum minigame state work, but stripped back to a general deterministic engine.

### State machine rules

- the machine is generic over context, enum state, event, and effect types
- the public API is explicit: `currentState()` and synchronous `dispatch(event)`
- handlers mutate machine owned context and return: `stay()` or `move(state)`, emitted effects, and timer commands
- state lifecycle hooks such as `onEnter` and `onExit` belong to the core definition DSL, not the public API
- the core owns serial event processing, lifecycle ordering, keyed timers, and dispatch report creation
- the core may define a scheduler abstraction, but it must not embed platform scheduler types
- the core must not know about players, worlds, or plugins
- timer commands schedule future events, and scheduling the same timer key replaces the previous timer
- `enqueue(event)` is runtime convenience in `state-machine-core`, not part of the public API
- guards do not belong in v1; a reducer may simply `stay()` when a condition fails
- state mutation should happen under one owner runtime or one serial dispatch context

Belongs in `state-machine-api`:
- machine contract
- reducer contract
- `DispatchResult`
- `ReducerResult`
- `StateChange`
- timer key and timer command vocabulary

Not in `state-machine-api`:
- definition builder DSL
- runtime implementation
- serial dispatch queue
- keyed timer registry
- scheduler abstraction
- effect sink integration
- guard types

Belongs in `state-machine-core`:
- definition builder DSL
- runtime implementation
- serial dispatch queue
- keyed timer registry
- scheduler abstraction
- effect sink integration
- enter and exit hook execution
- tests for order, reentry, timer replacement, cancellation, and queued dispatch behavior

Not allowed in the core:
- Bukkit scheduler
- Minestom scheduler
- proxy event buses
- gameplay rules specific to one minigame

Handlers should do:
- mutate machine owned context
- decide whether to `stay()` or `move(state)`
- emit typed effects
- request keyed timer scheduling or cancellation

Handlers should not do:
- teleport players directly
- query databases directly
- call platform APIs directly
- spawn async tasks directly

Instead:
- emit effects and let the owning service handle them

## Menu API

The menu surface is one API with two backends.
Not one API and two disconnected re implementations.

### Menu rules

- the public menu API must describe concepts that exist on both Paper and Minestom
- the shared core owns layout and state logic
- the shared core owns mandatory house chrome, breadcrumb back behavior, prompt-last behavior, wrapping, and progress rendering
- menu lore wrapping is character-count based: blocks stay on one line when they fit within `30` characters, otherwise the compiler rebalances them into the fewest reasonably even lines under that cap with `20` as the soft target, the item title never widens lore wrapping, and grouped `lines(...)` / `pairs(...)` lists stay one entry per line
- progress blocks use the fixed two-line house format, and the dash bar line carries strikethrough styling across both filled and empty segments so it reads as one continuous meter
- default menu prompts end with `!`, remain prompt-last unless prompt rendering is deliberately suppressed, render the left-click prompt as yellow `CLICK to ...!`, and render a contiguous aqua `RIGHT CLICK to ...!` line when a right-click interaction is present; shared nav arrows remain the fixed `Page N` edge-case chrome
- `tabs` uses row `0` for a centered grouped tab strip, row `1` for gray/lime nav chrome under visible tabs, and rows `2+` for tab content
- `tabs` uses the shared footer grammar by default and only opts out of it explicitly for custom canvas-style tab content
- the shared footer grammar is: slot `45` previous page, slot `48` back, slot `49` close, slot `53` next, and the remaining footer utility slots stay caller-owned
- shared back is runtime-owned rather than caller-authored: `context.open(...)` pushes menu/frame history, child menus render `&aGo Back` with the single lore line `&7To <menuname>`, and root opens do not auto-show back
- close uses simple shared chrome with no lore; navigation arrows use the standard shared format `&aPrevious Tab|Page` / `&aNext Tab|Page` with the single lore line `&ePage N`
- pure `list()` menus use a centered open `7x4` content panel at coordinates `(1,1)` through `(7,4)`; the interior stays blank by default and the left/right edge columns remain house-owned filler
- pure `list()` menus append `(N/M)` to the rendered inventory title only when they actually paginate; tabbed and canvas menus keep their authored titles
- menu interactions use the stock sound FX library by default: ordinary button actions use `menu/click`, page and tab-strip scroll chrome uses `menu/scroll`, and positive commit verbs such as `buy`, `claim`, and `confirm` use `result/confirm`
- special deny/result cases should override the interaction cue in the menu API, and when the stock cues are not enough callers should register or overlay their own cue in a `SoundCueService` and inject that sound service into the menu platform
- tab grouping is explicit; do not infer logical groups from tab names
- list-style tab content is a bordered `3x7` panel starting at slot `19`; its interior stays open by default, so authors should not try to fill every slot unless they are intentionally demonstrating paging, and overflowed list tabs page through the shared footer arrows in the bottom corners
- custom canvas-style tab content uses explicit slot placement below the nav rows, keeps black filler on by default, and may explicitly toggle that content-area filler off when the authored layout needs open slots
- canvas-style tab layouts should bias their primary placements onto row `3` and center them symmetrically: one item at `(4,3)`, two items at `(2,3)` and `(6,3)`, and expand outward from there
- the backends own inventory opening, click translation, and native item rendering
- `MenuButton` is for clickable items
- `MenuDisplayItem` is for read only items
- builder taxonomy matters: `list`, `tabs`, and `canvas` are separate for a reason
- menu titles use Adventure components; menu card content goes through the flat menu DSL
- platform-authored menu code should use native Paper or Minestom overloads instead of raw `MenuIcon`
- menu content is compiled eagerly into frames; dynamic changes should rebuild and reopen menus
- menu runtime identity must never depend on title text
- common menu features must stay inside the shared capability envelope

Belongs in `menu-api`:
- `Menu`
- builder contracts
- `MenuButton`
- `MenuDisplayItem`
- slot and layout contracts
- viewer neutral callback contracts

Belongs in `menu-core`:
- pagination math
- tab state
- viewport math
- slot resolution
- refresh policy
- neutral render model for menu items

Belongs in `menu-paper` and `menu-minestom`:
- native item conversion
- open and close handling
- click event mapping
- inventory holder or view integration
- platform lifecycle cleanup

Not allowed in `menu-api`:
- Bukkit `Material`
- Bukkit `ItemStack`
- Minestom `ItemStack`
- platform click event classes
- host specific scheduler logic

If a menu capability only exists on one backend, choose one of two options:
- keep it in a backend specific extension package
- reject it from the common API for now

Do not lie to yourself with a “universal” method that only one backend can really satisfy.

---

## Naming Rules

Prefer this naming pattern:
- `<feature>-api`
- `<feature>-core`
- `<feature>-<platform>`
- `<platform>-example`

Examples:
- `message-api`
- `message-core`
- `message-paper`
- `message-minestom`
- `message-velocity`

Avoid:
- `common-utils`
- `platform-common`
- `core-shared-platform-api`
- names that mean “miscellaneous shelf”

If the module name sounds like a junk drawer, it probably is one.

---

## Extraction Rules From Older Projects

When extracting from the uploaded codebases:
- prefer concepts over file copies
- preserve what is actually reusable
- delete project assumptions aggressively
- shrink the public API while keeping the power that mattered
- leave behind application specific loaders, services, and game content

A good extraction should feel cleaner than the source, not merely displaced.

---

## Runtime Authority Rule

Do not create runtime authority modules yet.

That means no `runtime-api`, no shared installed runtime plugin, and no synchronized cross plugin services in the first scaffold.

Revisit runtime authority only when there is a real need for:
- synchronized cooldowns
- shared authoritative caches
- one truth for multiple plugins on the same Paper server
- cross server coordination

Until then:
- keep common APIs host neutral
- keep implementations local and explicit
- keep the tree small

---

## Platform Standards

### Paper

- use the latest Paper APIs
- use lifecycle based Brigadier registration for any Paper commands or example commands
- do not use legacy `onCommand()` or `getCommand()` patterns
- keep NMS out of scope; if it ever becomes necessary, isolate it behind abstractions

### Minestom

- treat Minestom as an embedded host, not as a Paper clone
- do not design APIs around Bukkit assumptions and then try to repaint them
- keep scheduling and ownership explicit
- if the current official Minestom dependency requires a newer Java toolchain, isolate that requirement to the Minestom family instead of forcing every module to move with it

### Velocity

- Velocity is a messaging and orchestration host here, not a fake game server
- do not force menu or world oriented abstractions onto it

---

## Review Checklist

Before accepting a new feature or module, confirm:
- the correct repo-local skill was used when one exists for the touched feature family
- the boundary is real
- the public API is smaller than the source inspiration
- common modules contain no host types
- host modules stay thin
- the feature matrix is honest
- examples compile
- tests prove the shared engines, not only the adapters
- entity or NPC changes enforce House style through the shared service-entity layer
- any entity-layer proposal or design note shows caller usage, layer ownership, and resultant behavior
- nothing drifted into “we might need this later” territory

If a change makes the repository feel more like a platform and less like a library, stop and justify it explicitly.
