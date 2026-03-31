dependencies {
    api(project(":common:impulse-api"))
    implementation(project(":common:impulse-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
