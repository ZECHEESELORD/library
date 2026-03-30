dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    implementation(project(":common:sound-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
