dependencies {
    implementation(project(":common:data-memory"))
    implementation(project(":common:message-core"))
    implementation(project(":common:state-machine-core"))
    implementation(project(":common:menu-core"))
    implementation(project(":platform:paper:message-paper"))
    implementation(project(":platform:paper:menu-paper"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

tasks.processResources {
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version)
    }
}
