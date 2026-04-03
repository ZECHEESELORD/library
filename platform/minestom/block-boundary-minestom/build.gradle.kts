dependencies {
    api(project(":common:block-boundary-api"))
    api(project(":platform:minestom:block-grid-minestom"))
    implementation(project(":common:block-boundary-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
