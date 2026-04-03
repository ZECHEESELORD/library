dependencies {
    api(project(":common:block-boundary-api"))
    api(project(":platform:paper:block-grid-paper"))
    implementation(project(":common:block-boundary-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
