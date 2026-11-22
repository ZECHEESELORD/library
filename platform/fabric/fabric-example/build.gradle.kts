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

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
