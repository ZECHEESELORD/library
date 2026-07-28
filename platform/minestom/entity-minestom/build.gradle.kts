dependencies {
    api(project(":common:entity-api"))
    api(project(":common:house-service-entity"))
    api(project(":common:npc-behavior-api"))
    implementation(project(":common:entity-core"))
    implementation(project(":common:npc-behavior-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
