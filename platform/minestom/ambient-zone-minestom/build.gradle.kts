dependencies {
    api(project(":common:ambient-zone-api"))
    implementation(project(":common:ambient-zone-core"))
    api(project(":common:camera-motion-api"))
    api(project(":common:screen-overlay-api"))
    api(project(":common:sound-api"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
