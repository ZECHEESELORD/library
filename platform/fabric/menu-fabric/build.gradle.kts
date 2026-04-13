dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    implementation(project(":platform:fabric:sound-fabric"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
    implementation("net.kyori:adventure-text-serializer-plain:${rootProject.property("adventureVersion")}")
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
