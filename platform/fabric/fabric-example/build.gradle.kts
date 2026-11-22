plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    implementation(project(":common:message-core"))
    implementation(project(":platform:fabric:message-fabric"))
    implementation(project(":platform:fabric:menu-fabric"))
    implementation(project(":platform:fabric:sound-fabric"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
}

loom {
    runs {
        named("server") {
            runDirectory.set(layout.projectDirectory.dir("run/server"))
        }
        named("client") {
            runDirectory.set(layout.projectDirectory.dir("run/server"))
            generateRunConfig.set(false)
        }
    }
}

// Server gametests live in src/gametest and never reach the published jars or the release bundle.
fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("creative_library_fabric_example_gametest")
        enableGameTests.set(true)
        enableClientGameTests.set(false)
        // Accepts the Minecraft EULA for the throwaway gametest server run directory only.
        eula.set(true)
    }
}

loom.runs.named("gameTest") {
    property("fabric-api.gametest.report-file",
        layout.buildDirectory.file("reports/gametest/server.xml").get().asFile.absolutePath)
}

// Loom hooks runGameTest into check. Keep build and the release workflow free of game launches;
// run the gametests on purpose with :platform:fabric:fabric-example:runGameTest.
tasks.named("check") {
    setDependsOn(dependsOn.filterNot { dependency ->
        (dependency as? NamedDomainObjectProvider<*>)?.name == "runGameTest" ||
            (dependency as? Task)?.name == "runGameTest" ||
            dependency.toString() == "runGameTest"
    })
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
