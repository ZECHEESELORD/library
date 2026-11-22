plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    api(project(":common:movement-api"))
    api(project(":common:movement-core"))
    implementation(project(":common:hot-reload-core"))
    implementation(project(":platform:fabric:net-fabric"))
    implementation(project(":platform:fabric:telemetry-fabric"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to expandedVersion)
    }
}
