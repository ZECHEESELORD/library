dependencies {
    api(project(":common:sound-api"))
    implementation(project(":common:sound-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paper12111ApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}