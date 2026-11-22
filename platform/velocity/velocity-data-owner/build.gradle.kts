dependencies {
    implementation(project(":common:data-core"))
    implementation(project(":common:data-memory"))
    implementation(project(":common:data-yaml"))
    implementation(project(":common:data-mongodb"))
    implementation(project(":platform:velocity:data-velocity"))
    compileOnly("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
    compileOnly("org.slf4j:slf4j-api:${rootProject.property("slf4jVersion")}")
    annotationProcessor("com.velocitypowered:velocity-api:${rootProject.property("velocityApiVersion")}")
}
