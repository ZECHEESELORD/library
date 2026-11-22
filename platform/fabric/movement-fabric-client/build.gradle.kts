plugins {
    id("net.fabricmc.fabric-loom")
}

dependencies {
    api(project(":platform:fabric:movement-fabric"))
    implementation(project(":common:prediction-core"))
    implementation(project(":platform:fabric:net-fabric"))
    implementation(project(":platform:fabric:net-fabric-client"))
    implementation(project(":platform:fabric:camera-fabric-client"))
    implementation(project(":platform:fabric:telemetry-fabric-client"))
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
