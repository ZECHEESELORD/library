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

### Published Modules

Published:

- all `common/*` library modules
- platform adapter modules such as `message-paper`, `menu-fabric`, `menu-minestom`, and `message-velocity`

Not published:

- `paper-example`
- `paper-entity-example`
- `minestom-example`
- `minestom-entity-example`
- `velocity-example`
- `fabric-example`
- `fabric-client-example`
