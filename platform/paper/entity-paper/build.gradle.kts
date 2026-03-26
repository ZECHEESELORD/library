dependencies {
    api(project(":common:entity-api"))
    api(project(":common:house-service-entity"))
    implementation(project(":common:entity-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}
