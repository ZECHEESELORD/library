# library

Shared modules for Minecraft server and proxy development, split so a Paper plugin and a Fabric mod can lean on the same APIs instead of reimplementing the same scoreboard twice and disagreeing about the details.

[![CI](https://github.com/ZECHEESELORD/library/actions/workflows/ci.yml/badge.svg)](https://github.com/ZECHEESELORD/library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/ZECHEESELORD/library.svg)](https://jitpack.io/#ZECHEESELORD/library)
[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%20%7C%2025-orange.svg)](#compatibility)
[![Platforms](https://img.shields.io/badge/platforms-Paper%20%C2%B7%20Fabric%20%C2%B7%20Minestom%20%C2%B7%20Velocity-5b8a3a.svg)](#features)

Each module does one thing: scoreboards, cooldowns, tweening, entity capabilities, metrics. The logic for it lives in one place, behind a platform-neutral API, and the host-specific code stays a thin layer at the edge. You compile against the interface and pick up whichever adapter matches the server you're running.

---

## How the modules fit together

A feature ships in up to three layers, and the artifact names follow the layers:

| Suffix | Layer | What's in it |
| --- | --- | --- |
| `<feature>-api` | API | Interfaces, specs, and value types. No platform code. This is what you compile against. |
| `<feature>-core` | Core | The platform-neutral implementation: the logic that doesn't care whether it's on Paper or Minestom. |
| `<feature>-<platform>` | Adapter | The thin wiring that binds core to a specific host: `-paper`, `-minestom`, `-velocity`, `-fabric`. |

Pure-logic features (tween, cooldown, state-machine, metrics) stop at `api`/`core` and need no adapter. Anything that has to touch players, worlds, or packets gets one adapter per platform it supports.

> [!TIP]
> Compile against `-api`. At runtime you ship `-core` plus one `-<platform>` adapter. Most consumers never import more than that.

---

## Features

Modules marked **common** are platform-neutral and drop into any host as-is. The rest list the platforms with a shipped adapter.

### Spatial & world

| Module | What it does | Adapters |
| --- | --- | --- |
| `spatial` | Vectors, frames, anchors, bounds, and segments: the geometry the other systems sit on | common |
| `block-grid` | Block-aligned positions and bounds | Paper · Minestom |
| `block-boundary` | Region boundaries and cross-boundary allow/deny decisions | Paper · Minestom |

### Motion & game feel

| Module | What it does | Adapters |
| --- | --- | --- |
| `tween` | Keyed interpolation with easing, envelopes, hold behavior, and repeat modes | common |
| `curve` | Catmull-Rom spline paths with sampling and splitting | common |
| `camera-motion` | Server-driven camera moves with blend modes and per-viewer playback | Paper · Minestom |
| `trajectory-preview` | Projectile-path previews with collision queries and recompute policy | Paper · Minestom |
| `impulse` | Movement impulses (knockback, dashes) with stacking, compose modes, and axis masks | Paper · Minestom |
| `telegraph` | Wind-up indicators for incoming attacks and areas: shapes, timing, and viewer scope | Paper · Minestom |
| `ambient-zone` | Overlapping ambient zones blended per viewer along weight curves | Paper · Minestom |
| `screen-overlay` | Timed full-screen tints and vignettes with a conflict policy | Paper · Minestom |

### State & timing

| Module | What it does | Adapters |
| --- | --- | --- |
| `tick-lifecycle` | Tick-driven handles and instance-conflict policy shared by the timed systems | common |
| `state-machine` | Typed reducer state machines with timers | common |
| `cooldown` | Reserve and query cooldown windows by scope and key | common |

### Data

| Module | What it does | Adapters |
| --- | --- | --- |
| `data` | Storage-agnostic persistence with `-memory`, `-yaml`, and `-mongodb` backends | Paper · Minestom · Velocity · Fabric |

### Player-facing

| Module | What it does | Adapters |
| --- | --- | --- |
| `message` | Adventure text building: click and hover actions, pagination | Paper · Minestom · Velocity · Fabric |
| `sound` | Sound-cue playback abstraction | Paper · Minestom · Fabric |
| `scoreboard` | Sidebar scoreboards on a generic section model, with per-viewer overrides and transient sections | Paper · Minestom |
| `menu` | Inventory menus: list and canvas builders, actions, accent families | Paper · Minestom · Fabric |
| `ui-values` | A small text value with an optional color, shared across the UI modules | common |

### Entities

| Module | What it does | Adapters |
| --- | --- | --- |
| `entity` | Capability-based entities: display, AI, equipment, pose, skin, variant, leash, passenger, and more | Paper · Minestom · Fabric |
| `house-service-entity` | A ready-made service NPC built on `entity-core` | common |

> [!NOTE]
> Paper gets an optional `entity-paper-citizens` bridge for Citizens-backed NPCs. And there is no Velocity scoreboard adapter, because a proxy cannot honestly render a Minecraft sidebar.

### Observability

| Module | What it does | Adapters |
| --- | --- | --- |
| `metrics` | A `Telemetry` facade with an in-memory registry, JVM/process collectors, and Prometheus export (`-prometheus`) | common |

---

## Install

Published through JitPack under:

```text
com.github.ZECHEESELORD.library:<module>:<tag>
```

### Repositories

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add the rest only for the adapters you actually pull in:

| Repository | Needed for |
| --- | --- |
| `https://maven.fabricmc.net/` | Fabric adapters |
| `https://repo.papermc.io/repository/maven-public/` | Paper adapters |
| `https://maven.citizensnpcs.co/repo` | the Citizens bridge |
| `https://repo.minebench.de` | Velocity adapters |

### Coordinates

Import the BOM once, then name the modules without repeating the tag:

```kotlin
dependencies {
    implementation(platform("com.github.ZECHEESELORD.library:library-bom:v7"))

    implementation("com.github.ZECHEESELORD.library:cooldown-api")
    implementation("com.github.ZECHEESELORD.library:cooldown-core")
    implementation("com.github.ZECHEESELORD.library:scoreboard-api")
    implementation("com.github.ZECHEESELORD.library:scoreboard-core")
    implementation("com.github.ZECHEESELORD.library:scoreboard-paper")
    implementation("com.github.ZECHEESELORD.library:message-velocity")
}
```

> [!IMPORTANT]
> The BOM manages this repo's published artifacts only. Host dependencies (Paper, Fabric, Minestom, Velocity, Citizens, MongoDB) still come from their own repositories, at the versions your chosen modules require.

<details>
<summary>Full list of published artifacts</summary>

Published:

- every `common/*` module (the `-api`, `-core`, and backend artifacts listed above)
- platform adapters under each host, e.g. `message-paper`, `scoreboard-minestom`, `menu-fabric`, `data-velocity`, `entity-paper-citizens`

Legacy lanes carry a version suffix and exist only where the source has actually diverged from the latest lane. Examples: `scoreboard-paper-1_21_11`, `message-fabric-1_21_11`, and `menu-fabric-1_21_11`.

Not published (worked examples, kept in-tree for reference):

`paper-example`, `paper-entity-example`, `minestom-example`, `minestom-entity-example`, `velocity-example`, `fabric-example`, `fabric-client-example`

</details>

---

## Compatibility

| Lane | Target | Java |
| --- | --- | --- |
| Common modules | platform-neutral | 21 |
| Paper (latest) | Paper API `26.1.2.build.66-stable` (MC `26.1.2`) | 25 |
| Paper (legacy) | Paper API `1.21.11-R0.1-SNAPSHOT` | 21 |
| Fabric (latest) | MC `26.1.2`, Loader `0.19.1`, API `0.145.4+26.1.2` | 25 |
| Fabric (legacy) | MC `1.21.11`, Loader `0.18.6`, API `0.141.3+1.21.11` | 21 |
| Minestom | `2026.03.03-1.21.11` | 25 |
| Velocity | `3.3.0-SNAPSHOT` | 21 |

All text rides on Adventure `4.17.0`.

> [!WARNING]
> The Java 25 lanes need a toolchain that supports it. IntelliJ IDEA `2025.3` or newer is recommended when working on the latest Paper and Fabric modules.

---

## Usage

A scoreboard, end to end. Register a board, show it, override a section for one viewer, then push a short-lived notice:

```java
ScoreboardSpec board = ScoreboardSpec.builder(Key.key("example", "main"))
        .title(Component.text("Status"))
        .fixedSection("info", Component.text("Line 1"), Component.text("Line 2"))
        .section("activity", context -> List.of(currentLine(context)))
        .build();

scoreboards.register(board);
scoreboards.show(playerId, board.key());

scoreboards.overrideSection(
        playerId,
        "activity",
        ScoreboardSection.fixed("activity", Component.text("Temporary replacement"))
);

scoreboards.pushTransient(playerId, TransientSectionSpec.builder(Key.key("example", "notice"))
        .section(ScoreboardSection.fixed("notice", Component.text("Short-lived notice")))
        .placement(TransientPlacement.TOP)
        .ttlTicks(60)
        .build());
```

The board knows about ordered sections, per-viewer overrides, and tick-based transients. It does not know your domain; names, data, and meaning stay in the consumer plugin.

<details>
<summary>Metrics: timing, JVM gauges, and a Prometheus endpoint</summary>

Time a block of work under a label:

```java
LabelKey status = Metrics.label("status", "success", "failure");
TimerMetric chunkGeneration = Metrics.timer(
        "chunk_generation_seconds",
        "Tracks chunk generation latency",
        status
);

StandardTelemetry telemetry = new StandardTelemetry();
telemetry.observe(
        chunkGeneration,
        MetricLabels.of(status, "success"),
        () -> generateChunk(pos)
);
```

Expose a Prometheus scrape from a plain JVM process:

```java
StandardTelemetry telemetry = new StandardTelemetry();
MetricRegistration jvmMetrics = JvmMetricsBinder.bind(telemetry);
PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
        new InetSocketAddress("127.0.0.1", 9464),
        "/metrics",
        telemetry
);
```

Wire a low-cardinality platform gauge straight through the shared API:

```java
GaugeMetric playersOnline = Metrics.gauge(
        "players_online",
        "Current online player count",
        "players"
);

telemetry.registerGauge(playersOnline, MetricLabels.empty(), server::getPlayerCount);
```

Annotation-based instrumentation is deferred on purpose. The primary path is explicit `observe(...)`, direct counter and gauge updates, and explicit export wiring.

</details>

---

## Building from source

```bash
./gradlew build
```

You need JDK 21 and JDK 25 available, since the lanes target both. The example modules build with everything else but are not published.

> [!NOTE]
> Fabric builds wire Minecraft through Loom's `minecraft(...)` configuration; everything else (mods, libraries, and intra-repo `project(...)` dependencies) uses ordinary Gradle wiring. Jars come from the standard `jar` task, not `remapJar`.

---

## License

Licensed under [`GPL-3.0-only`](LICENSE).

> [!CAUTION]
> This is a copyleft license. Distributed forks and dependent works must comply with the GPL v3 source-sharing terms: if you ship it, you ship the source.
