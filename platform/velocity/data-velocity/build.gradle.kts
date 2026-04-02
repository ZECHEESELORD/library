dependencies {
    api(project(":common:data-core"))
    compileOnlyApi("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
}
