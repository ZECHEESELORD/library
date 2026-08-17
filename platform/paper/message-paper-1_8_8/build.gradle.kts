dependencies {
    api(project(":common:message-api"))
    implementation(project(":common:message-core"))
    implementation("net.kyori:adventure-platform-bukkit:${rootProject.property("adventurePlatformBukkitVersion")}")
    compileOnlyApi("org.spigotmc:spigot-api:${rootProject.property("spigot188ApiVersion")}")
    testImplementation("org.spigotmc:spigot-api:${rootProject.property("spigot188ApiVersion")}")
}
