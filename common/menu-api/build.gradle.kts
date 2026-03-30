dependencies {
    api("net.kyori:adventure-api:${rootProject.property("adventureVersion")}")
    api(project(":common:sound-api"))
    api(project(":common:ui-values-api"))
}
