plugins {
    id("net.fabricmc.fabric-loom-remap")
}

dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    implementation(project(":platform:fabric:sound-fabric-1_21_11"))
    minecraft("com.mojang:minecraft:${rootProject.property("fabric12111MinecraftVersion")}")
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("fabric12111LoaderVersion")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric12111ApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
    implementation("net.kyori:adventure-text-serializer-plain:${rootProject.property("adventureVersion")}")
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
