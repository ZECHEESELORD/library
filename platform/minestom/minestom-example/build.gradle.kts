dependencies {
    implementation(project(":common:data-memory"))
    implementation(project(":common:message-core"))
    implementation(project(":common:state-machine-core"))
    implementation(project(":common:menu-core"))
    implementation(project(":platform:minestom:message-minestom"))
    implementation(project(":platform:minestom:menu-minestom"))
    implementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}
