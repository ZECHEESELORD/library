dependencies {
    implementation(project(":common:data-core"))
    implementation(project(":common:data-memory"))
    implementation(project(":common:data-yaml"))
    implementation(project(":common:data-mongodb"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}
