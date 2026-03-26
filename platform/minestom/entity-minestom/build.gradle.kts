dependencies {
    api(project(":common:entity-api"))
    api(project(":common:house-service-entity"))
    implementation(project(":common:entity-core"))
    compileOnlyApi("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
