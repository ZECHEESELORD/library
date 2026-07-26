dependencies {
    api(project(":common:npc-behavior-api"))
    implementation("net.kyori:adventure-text-serializer-plain:${rootProject.property("adventureVersion")}")
    testImplementation(project(":common:entity-core"))
}
