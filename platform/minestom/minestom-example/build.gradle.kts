plugins {
    application
}

dependencies {
    implementation(project(":common:cooldown-api"))
    implementation(project(":common:cooldown-core"))
    implementation(project(":common:data-memory"))
    implementation(project(":common:house-service-entity"))
    implementation(project(":common:message-core"))
    implementation(project(":common:state-machine-core"))
    implementation(project(":common:menu-core"))
    implementation(project(":common:spatial-core"))
    implementation(project(":common:tween-core"))
    implementation(project(":common:curve-core"))
    implementation(project(":common:trajectory-preview-core"))
    implementation(project(":platform:minestom:entity-minestom"))
    implementation(project(":platform:minestom:message-minestom"))
    implementation(project(":platform:minestom:sound-minestom"))
    implementation(project(":platform:minestom:camera-motion-minestom"))
    implementation(project(":platform:minestom:screen-overlay-minestom"))
    implementation(project(":platform:minestom:menu-minestom"))
    implementation(project(":platform:minestom:telegraph-minestom"))
    implementation(project(":platform:minestom:trajectory-preview-minestom"))
    implementation(project(":platform:minestom:impulse-minestom"))
    implementation(project(":platform:minestom:ambient-zone-minestom"))
    implementation("net.minestom:minestom:${rootProject.property("minestomVersion")}")
}

application {
    mainClass.set("sh.harold.creative.library.example.minestom.MinestomExampleBootstrap")
}

tasks.named<org.gradle.api.tasks.JavaExec>("run") {
    group = "application"
    description = "Runs the embedded Minestom dev harness on localhost:25565."
}
