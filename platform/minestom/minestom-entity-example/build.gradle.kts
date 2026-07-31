plugins {
    application
}

dependencies {
    implementation(project(":common:house-service-entity"))
    implementation(project(":platform:minestom:entity-minestom"))
    implementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}

application {
    mainClass.set("sh.harold.library.example.minestom.entity.MinestomEntityExampleBootstrap")
}

tasks.named<org.gradle.api.tasks.JavaExec>("run") {
    group = "application"
    description = "Runs the standalone Minestom NPC behavior dioramas on localhost:25565."
}
