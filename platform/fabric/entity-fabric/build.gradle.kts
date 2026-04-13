dependencies {
    api(project(":common:entity-api"))
    api(project(":common:house-service-entity"))
    implementation(project(":common:entity-core"))
    minecraft("com.mojang:minecraft:${rootProject.property("minecraftVersion")}")
    implementation("net.fabricmc:fabric-loader:${rootProject.property("fabricLoaderVersion")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabricApiVersion")}")
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
