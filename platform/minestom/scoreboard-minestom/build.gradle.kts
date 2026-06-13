dependencies {
    api(project(":common:scoreboard-api"))
    implementation(project(":common:scoreboard-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
