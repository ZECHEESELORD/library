dependencies {
    api(project(":common:screen-overlay-api"))
    implementation(project(":common:screen-overlay-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
