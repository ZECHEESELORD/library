# library

Shared cross platform library modules for Paper, Fabric, Minestom, and Velocity.

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

Examples:

```kotlin
dependencies {
    implementation("com.github.ZECHEESELORD.library:cooldown-api:<tag>")
    implementation("com.github.ZECHEESELORD.library:cooldown-core:<tag>")
    implementation("com.github.ZECHEESELORD.library:message-paper:<tag>")
    implementation("com.github.ZECHEESELORD.library:entity-minestom:<tag>")
    implementation("com.github.ZECHEESELORD.library:message-velocity:<tag>")
}
```

### Java Compatibility

- Common modules, Paper adapters, and Velocity adapters target Java 21.
- Minestom adapters target Java 25.
- Fabric adapters target Java 25.

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
