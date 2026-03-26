dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
