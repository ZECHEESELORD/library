dependencies {
    implementation(project(":common:data-memory"))
    implementation(project(":common:message-core"))
    implementation(project(":common:state-machine-core"))
    implementation(project(":platform:velocity:message-velocity"))
    compileOnly("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:2.0.13")
    annotationProcessor("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
}
