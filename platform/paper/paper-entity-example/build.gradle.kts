dependencies {
    implementation(project(":common:house-service-entity"))
    implementation(project(":platform:paper:entity-paper"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly("com.github.retrooper:packetevents-spigot:${rootProject.property("packetEventsVersion")}")
}

tasks.processResources {
    val expandedVersion = project.version.toString()
    inputs.property("version", expandedVersion)
    filesMatching("paper-plugin.yml") {
        expand("version" to expandedVersion)
    }
}
