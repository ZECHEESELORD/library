dependencies {
    api(project(":common:scoreboard-api"))
    implementation(project(":common:scoreboard-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}
