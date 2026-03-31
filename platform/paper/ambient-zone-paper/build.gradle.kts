dependencies {
    api(project(":common:ambient-zone-api"))
    implementation(project(":common:ambient-zone-core"))
    api(project(":common:camera-motion-api"))
    api(project(":common:screen-overlay-api"))
    api(project(":common:sound-api"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
