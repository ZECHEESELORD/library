dependencies {
    api(project(":common:screen-overlay-api"))
    implementation(project(":common:screen-overlay-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
