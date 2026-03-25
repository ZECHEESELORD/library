# AGENTS.md

## Scope

These instructions apply to all design, extraction, and implementation work for the new shared cross platform library.

Current target hosts:
- Paper
- Minestom
- Velocity

Current approved feature families:
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

If local skills exist for menus, commands, or messaging, treat them as implementation standards.
In particular:
- the menu skill is authoritative for menu UX, builder selection, and `MenuButton` versus `MenuDisplayItem`
- the Paper command skill is authoritative for any Paper command examples or bootstrap commands
- the player message facade skill is authoritative for player facing messaging patterns in samples and adapters

If there is a conflict:
- this file is the source of truth for architecture, boundaries, and extraction policy
- the menu skill is the source of truth for menu taxonomy and UX details
- the command skill is the source of truth for Paper command registration style
- the message skill is the source of truth for player facing message usage

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

---

## Current Module Plan

The approved first cut is this.

```text
common/
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
    message-paper/
    menu-paper/
    paper-example/
  minestom/
    message-minestom/
    menu-minestom/
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
- `message-paper`, `message-minestom`, `message-velocity` are sender adapters only
- `menu-paper` and `menu-minestom` translate the shared menu model into native inventory behavior
- example modules prove wiring and keep the integration honest

### What is intentionally absent

Not in v1:
- scoreboard modules
- NPC modules
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
- the backends own inventory opening, click translation, and native item rendering
- `MenuButton` is for clickable items
- `MenuDisplayItem` is for read only items
- builder taxonomy matters: list, tabbed, and custom are separate for a reason
- titles and lore use Adventure components
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
- the boundary is real
- the public API is smaller than the source inspiration
- common modules contain no host types
- host modules stay thin
- the feature matrix is honest
- examples compile
- tests prove the shared engines, not only the adapters
- nothing drifted into “we might need this later” territory

If a change makes the repository feel more like a platform and less like a library, stop and justify it explicitly.
