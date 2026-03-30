dependencies {
    api(project(":common:screen-overlay-api"))
    implementation(project(":common:screen-overlay-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
