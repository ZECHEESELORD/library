# library

Shared cross platform library modules for Paper, Fabric, Minestom, and Velocity.

### License

This project is licensed under `GPL-3.0-only`. Distributed forks and dependent works must comply with the GPL v3 source-sharing terms.

### Repositories

Add JitPack plus the repositories needed by the modules you consume:

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

Add these only when needed:

- Fabric adapters: `maven("https://maven.fabricmc.net/")`
- Paper adapters: `maven("https://repo.papermc.io/repository/maven-public/")`
- Citizens bridge: `maven("https://maven.citizensnpcs.co/repo")`
- Velocity adapters: `maven("https://repo.minebench.de")`

### Coordinates

JitPack publishes this multi-module repo under:

```text
com.github.ZECHEESELORD.library:<module>:<tag>
```

Import the BOM once, then declare only the modules you need without repeating the tag:

```kotlin
dependencies {
    implementation(platform("com.github.ZECHEESELORD.library:library-bom:<tag>"))
    implementation("com.github.ZECHEESELORD.library:cooldown-api")
    implementation("com.github.ZECHEESELORD.library:cooldown-core")
    implementation("com.github.ZECHEESELORD.library:message-paper")
    implementation("com.github.ZECHEESELORD.library:message-paper-1_21_11")
    implementation("com.github.ZECHEESELORD.library:scoreboard-api")
    implementation("com.github.ZECHEESELORD.library:scoreboard-core")
    implementation("com.github.ZECHEESELORD.library:scoreboard-paper")
    implementation("com.github.ZECHEESELORD.library:entity-minestom")
    implementation("com.github.ZECHEESELORD.library:message-velocity")
}
```

The BOM manages this repo's published library artifacts only. Host dependencies such as Paper,
Fabric, Minestom, Velocity, Citizens, and MongoDB still come from the repositories and versions
required by the modules you choose.

### Java Compatibility

- Common modules, Paper `*-1_21_11` adapters, and Velocity adapters target Java 21.
- Unsuffixed Paper adapters target Java 25 for the latest Paper lane.
- Minestom adapters target Java 25.
- Fabric adapters target Java 25.

### Paper 26.1 Notes

- Unsuffixed Paper modules target Paper API `26.1.2.build.66-stable` and Java `25`.
- Paper example plugin descriptors advertise `api-version: '26.1.2'`.
- Use the unsuffixed artifact names such as `message-paper`, `menu-paper`, and `entity-paper` for latest Paper consumers.

### Paper 1.21.11 Notes

- Legacy Paper modules use version-suffixed artifact names such as `message-paper-1_21_11`.
- The `1.21.11` Paper lane targets Java `21` and Paper API `1.21.11-R0.1-SNAPSHOT`.
- Keep 1.21.11 compatibility in suffixed Paper modules instead of adding version checks to common APIs or consumer plugins.

### Fabric 26.1 Notes

- Fabric modules target Minecraft `26.1.2` with Fabric Loader `0.19.1`, Fabric API `0.145.4+26.1.2`, and Loom `1.16.1`.
- The repo uses the non-remapping Loom plugin id `net.fabricmc.fabric-loom`.
- Fabric builds still declare Minecraft itself on Loom's `minecraft(...)` configuration, while mod, library, and intra-repo project dependencies use normal Gradle wiring such as `implementation`, `compileOnly`, and plain `project(...)`.
- Fabric jars are built with the normal `jar` task; do not wire release flow around `remapJar`.
- IntelliJ IDEA `2025.3` or newer is recommended for Java 25 support when working on the Fabric modules.

### Fabric 1.21.11 Notes

- The repo now has a versioned legacy adapter lane starting with `message-fabric-1_21_11`.
- `data-fabric-1_21_11` follows the same pattern.
- The `1.21.11` lane targets Java `21` and uses the remap Loom plugin id `net.fabricmc.fabric-loom-remap`.
- Keep legacy Fabric modules version-suffixed so the `26.1.2` lane remains unchanged and publishable.

### Published Modules

Published:

- all `common/*` library modules
- platform adapter modules such as `message-paper`, `menu-fabric`, `menu-minestom`, and `message-velocity`

### Scoreboard Modules

Scoreboards use a generic section model. Common code knows about boards, ordered sections, per-viewer section overrides, and tick-based transient sections. Consumer plugins own all domain data and names.

Available modules:

- `scoreboard-api`
- `scoreboard-core`
- `scoreboard-paper`
- `scoreboard-paper-1_21_11`
- `scoreboard-minestom`

There is no Velocity scoreboard adapter because a proxy cannot honestly render a Minecraft sidebar.

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

### Metrics Modules

- `metrics-api` defines metric descriptors plus the platform-agnostic `Telemetry` facade
- `metrics-core` provides the default in-memory registry and JVM/process collectors
- `metrics-prometheus` renders Prometheus scrapes and includes an optional JDK `HttpServer` helper

Manual timing instrumentation:

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

Expose a Prometheus scrape endpoint from a generic JVM app:

```java
StandardTelemetry telemetry = new StandardTelemetry();
MetricRegistration jvmMetrics = JvmMetricsBinder.bind(telemetry);
PrometheusHttpExporter exporter = PrometheusHttpExporter.start(
        new InetSocketAddress("127.0.0.1", 9464),
        "/metrics",
        telemetry
);
```

Paper, Fabric, and other hosts can wire low-cardinality platform gauges directly through the shared API:

```java
GaugeMetric playersOnline = Metrics.gauge(
        "players_online",
        "Current online player count",
        "players"
);

telemetry.registerGauge(playersOnline, MetricLabels.empty(), server::getPlayerCount);
```

Annotation-based instrumentation is intentionally deferred from v1. The primary path is explicit `observe(...)`, direct counter/gauge updates, and explicit Prometheus export wiring.

Not published:

- `paper-example`
- `paper-entity-example`
- `minestom-example`
- `minestom-entity-example`
- `velocity-example`
- `fabric-example`
- `fabric-client-example`
