dependencies {
    implementation(project(":common:house-service-entity"))
    implementation(project(":platform:paper:entity-paper"))
    implementation(project(":platform:paper:entity-paper-citizens"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}
