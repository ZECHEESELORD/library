dependencies {
    api(project(":common:telegraph-api"))
    implementation(project(":common:telegraph-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
