dependencies {
    api(project(":common:scoreboard-api"))
    implementation(project(":common:scoreboard-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
}
