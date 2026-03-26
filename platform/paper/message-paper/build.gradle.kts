dependencies {
    api(project(":common:message-api"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}
