plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    implementation(project(":common:movement-api"))
    implementation(project(":common:movement-core"))
    implementation(project(":common:camera-api"))
    implementation(project(":platform:fabric:movement-fabric"))
    implementation(project(":platform:fabric:movement-fabric-client"))
    implementation(project(":platform:fabric:camera-fabric-client"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
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

// Client gametests for the feel layer live in src/gametest: they drive the five moves with simulated input,
// record per-tick telemetry CSVs and assert 02's tick timelines.
fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("creative_library_fabric_feel_example_gametest")
        enableGameTests.set(false)
        enableClientGameTests.set(true)
        // Accepts the Minecraft EULA for the throwaway gametest run directory only.
        eula.set(true)
    }
}

dependencies {
    // Load the library Fabric mods the feel example needs in the client gametest world.
    listOf(
        ":platform:fabric:net-fabric",
        ":platform:fabric:net-fabric-client",
        ":platform:fabric:telemetry-fabric",
        ":platform:fabric:telemetry-fabric-client",
    ).forEach { "gametestRuntimeOnly"(project(it)) }
    // The gametests record telemetry, read the handshake clock and step frames with the capture clock.
    listOf(
        ":common:telemetry-api",
        ":common:telemetry-core",
        ":platform:fabric:telemetry-fabric",
        ":platform:fabric:telemetry-fabric-client",
        ":platform:fabric:net-fabric-client",
        ":platform:fabric:devtools-fabric-client",
    ).forEach { "gametestImplementation"(project(it)) }
}

// Client gametests launch a game, so they stay out of build; check still compiles them.
tasks.named("check") {
    dependsOn("compileGametestJava")
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
