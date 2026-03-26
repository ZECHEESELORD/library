dependencies {
    implementation(project(":common:house-service-entity"))
    implementation(project(":platform:minestom:entity-minestom"))
    implementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
