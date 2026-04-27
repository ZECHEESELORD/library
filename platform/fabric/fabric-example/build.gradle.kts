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
            runDir("run/server")
        }
        named("client") {
            runDir("run/server")
            ideConfigGenerated(false)
        }
    }
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
