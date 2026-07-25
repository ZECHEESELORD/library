dependencies {
    api(project(":common:entity-api"))
    api(project(":common:spatial-api"))
    api("net.kyori:adventure-api:${rootProject.property("adventureVersion")}")
}
