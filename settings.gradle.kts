pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
    plugins {
        id("net.fabricmc.fabric-loom") version "1.16.1"
        id("net.fabricmc.fabric-loom-remap") version "1.16.1"
    }
}

rootProject.name = "library"

include(
    ":common:tick-lifecycle-api",
    ":common:spatial-api",
    ":common:spatial-core",
    ":common:block-grid-api",
    ":common:block-boundary-api",
    ":common:block-boundary-core",
    ":common:tween-api",
    ":common:tween-core",
    ":common:curve-api",
    ":common:curve-core",
    ":common:telegraph-api",
    ":common:telegraph-core",
    ":common:trajectory-preview-api",
    ":common:trajectory-preview-core",
    ":common:impulse-api",
    ":common:impulse-core",
    ":common:ambient-zone-api",
    ":common:ambient-zone-core",
    ":common:camera-motion-api",
    ":common:camera-motion-core",
    ":common:data-api",
    ":common:data-core",
    ":common:data-memory",
    ":common:data-yaml",
    ":common:data-mongodb",
    ":common:cooldown-api",
    ":common:cooldown-core",
    ":common:ui-values-api",
    ":common:screen-overlay-api",
    ":common:screen-overlay-core",
    ":common:message-api",
    ":common:message-core",
    ":common:metrics-api",
    ":common:metrics-core",
    ":common:metrics-prometheus",
    ":common:sound-api",
    ":common:sound-core",
    ":common:state-machine-api",
    ":common:state-machine-core",
    ":common:menu-api",
    ":common:menu-core",
    ":common:entity-api",
    ":common:entity-core",
    ":common:house-service-entity",
    ":platform:paper:message-paper",
    ":platform:paper:sound-paper",
    ":platform:paper:camera-motion-paper",
    ":platform:paper:block-grid-paper",
    ":platform:paper:block-boundary-paper",
    ":platform:paper:screen-overlay-paper",
    ":platform:paper:telegraph-paper",
    ":platform:paper:trajectory-preview-paper",
    ":platform:paper:impulse-paper",
    ":platform:paper:ambient-zone-paper",
    ":platform:paper:data-paper",
    ":platform:paper:paper-data-owner",
    ":platform:paper:menu-paper",
    ":platform:paper:paper-example",
    ":platform:paper:entity-paper",
    ":platform:paper:entity-paper-citizens",
    ":platform:paper:paper-entity-example",
    ":platform:minestom:message-minestom",
    ":platform:minestom:sound-minestom",
    ":platform:minestom:camera-motion-minestom",
    ":platform:minestom:block-grid-minestom",
    ":platform:minestom:block-boundary-minestom",
    ":platform:minestom:screen-overlay-minestom",
    ":platform:minestom:telegraph-minestom",
    ":platform:minestom:trajectory-preview-minestom",
    ":platform:minestom:impulse-minestom",
    ":platform:minestom:ambient-zone-minestom",
    ":platform:minestom:data-minestom",
    ":platform:minestom:minestom-data-owner",
    ":platform:minestom:menu-minestom",
    ":platform:minestom:minestom-example",
    ":platform:minestom:entity-minestom",
    ":platform:minestom:minestom-entity-example",
    ":platform:velocity:message-velocity",
    ":platform:velocity:data-velocity",
    ":platform:velocity:velocity-data-owner",
    ":platform:velocity:velocity-example",
    ":platform:fabric:message-fabric",
    ":platform:fabric:message-fabric-1_21_11",
    ":platform:fabric:message-fabric-client",
    ":platform:fabric:data-fabric",
    ":platform:fabric:data-fabric-1_21_11",
    ":platform:fabric:sound-fabric",
    ":platform:fabric:sound-fabric-1_21_11",
    ":platform:fabric:sound-fabric-client",
    ":platform:fabric:menu-fabric",
    ":platform:fabric:menu-fabric-1_21_11",
    ":platform:fabric:menu-fabric-client",
    ":platform:fabric:entity-fabric",
    ":platform:fabric:entity-fabric-client",
    ":platform:fabric:fabric-example",
    ":platform:fabric:fabric-client-example",
)

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.PREFER_PROJECT)

    repositories {
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.citizensnpcs.co/repo")
        maven("https://repo.minebench.de")
    }
}
