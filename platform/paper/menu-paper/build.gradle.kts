dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    compileOnlyApi("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}
