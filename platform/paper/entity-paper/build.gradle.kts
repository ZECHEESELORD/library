dependencies {
    api(project(":common:entity-api"))
    api(project(":common:npc-behavior-api"))
    api(project(":common:house-service-entity"))
    implementation(project(":common:entity-core"))
    implementation(project(":common:npc-behavior-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnlyApi("com.github.retrooper:packetevents-spigot:${rootProject.property("packetEventsVersion")}")
    testRuntimeOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testRuntimeOnly("com.github.retrooper:packetevents-spigot:${rootProject.property("packetEventsVersion")}")
}
