dependencies {
    api(project(":common:block-boundary-api"))
    api(project(":platform:paper:block-grid-paper-1_21_11"))
    implementation(project(":common:block-boundary-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
