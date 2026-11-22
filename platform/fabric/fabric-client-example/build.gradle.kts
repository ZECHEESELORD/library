plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    implementation(project(":common:message-core"))
    implementation(project(":platform:fabric:message-fabric-client"))
    implementation(project(":platform:fabric:sound-fabric-client"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
}

loom {
    runs {
        named("client") {
            runDirectory.set(layout.projectDirectory.dir("run/client"))
        }
        named("server") {
            runDirectory.set(layout.projectDirectory.dir("run/client"))
            generateRunConfig.set(false)
        }
    }
}

// Client gametests live in src/gametest and never reach the published jars or the release bundle.
fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("creative_library_fabric_client_example_gametest")
        enableGameTests.set(false)
        enableClientGameTests.set(true)
        // Accepts the Minecraft EULA for the throwaway gametest run directory only.
        eula.set(true)
    }
}

dependencies {
    // Load every latest-lane library Fabric mod, plus the server example, in the client gametest world.
    listOf(
        ":platform:fabric:data-fabric",
        ":platform:fabric:entity-fabric",
        ":platform:fabric:entity-fabric-client",
        ":platform:fabric:menu-fabric",
        ":platform:fabric:menu-fabric-client",
        ":platform:fabric:message-fabric",
        ":platform:fabric:sound-fabric",
        ":platform:fabric:fabric-example",
    ).forEach { "gametestRuntimeOnly"(project(it)) }
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
