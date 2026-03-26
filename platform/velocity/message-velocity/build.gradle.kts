dependencies {
    api(project(":common:message-api"))
    compileOnlyApi("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
}
