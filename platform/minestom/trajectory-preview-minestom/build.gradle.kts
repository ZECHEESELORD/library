dependencies {
    api(project(":common:trajectory-preview-api"))
    implementation(project(":common:trajectory-preview-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
