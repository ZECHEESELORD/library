plugins {
    application
}

dependencies {
    implementation(project(":common:data-core"))
    implementation(project(":common:data-memory"))
    implementation(project(":common:data-yaml"))
    implementation(project(":common:data-mongodb"))
    implementation(project(":platform:minestom:data-minestom"))
    implementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}

application {
    mainClass.set("sh.harold.creative.library.data.minestom.owner.MinestomDataOwnerBootstrap")
}
