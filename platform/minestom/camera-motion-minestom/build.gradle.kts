dependencies {
    api(project(":common:camera-motion-api"))
    implementation(project(":common:camera-motion-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
