dependencies {
    api(project(":common:trajectory-preview-api"))
    implementation(project(":common:trajectory-preview-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
