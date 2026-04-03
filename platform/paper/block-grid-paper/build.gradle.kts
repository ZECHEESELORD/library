dependencies {
    api(project(":common:block-grid-api"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
