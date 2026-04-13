dependencies {
    api(project(":common:message-api"))
    implementation(project(":common:message-core"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
