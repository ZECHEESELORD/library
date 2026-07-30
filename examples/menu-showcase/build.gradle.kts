dependencies {
    api(project(":common:menu-core"))
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
}
