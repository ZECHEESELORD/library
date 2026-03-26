dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
