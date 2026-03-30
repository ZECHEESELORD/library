dependencies {
    api(project(":common:camera-motion-api"))
    implementation(project(":common:camera-motion-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
