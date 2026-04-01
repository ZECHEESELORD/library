# library

Shared cross platform library modules for Paper, Minestom, and Velocity.

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

### Published Modules

Published:

- all `common/*` library modules
- platform adapter modules such as `message-paper`, `menu-minestom`, and `message-velocity`

Not published:

- `paper-example`
- `paper-entity-example`
- `minestom-example`
- `minestom-entity-example`
- `velocity-example`

