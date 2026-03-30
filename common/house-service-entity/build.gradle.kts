dependencies {
    api(project(":common:entity-api"))
    implementation(project(":common:entity-core"))
    implementation("net.kyori:adventure-text-minimessage:${rootProject.property("adventureVersion")}")
    implementation("net.kyori:adventure-text-serializer-legacy:${rootProject.property("adventureVersion")}")
}
