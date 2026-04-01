dependencies {
    implementation(project(":common:cooldown-api"))
    implementation(project(":common:cooldown-core"))
    implementation(project(":common:data-memory"))
    implementation(project(":common:message-core"))
    implementation(project(":common:state-machine-core"))
    implementation(project(":common:menu-core"))
    implementation(project(":common:spatial-core"))
    implementation(project(":common:tween-core"))
    implementation(project(":common:curve-core"))
    implementation(project(":common:trajectory-preview-core"))
    implementation(project(":platform:paper:message-paper"))
    implementation(project(":platform:paper:sound-paper"))
    implementation(project(":platform:paper:camera-motion-paper"))
    implementation(project(":platform:paper:screen-overlay-paper"))
    implementation(project(":platform:paper:menu-paper"))
    implementation(project(":platform:paper:telegraph-paper"))
    implementation(project(":platform:paper:trajectory-preview-paper"))
    implementation(project(":platform:paper:impulse-paper"))
    implementation(project(":platform:paper:ambient-zone-paper"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}
