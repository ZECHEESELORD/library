dependencies {
    api(project(":common:data-core"))
    api("org.mongodb:mongodb-driver-sync:${rootProject.property("mongodbDriverVersion")}")

    testImplementation("org.testcontainers:junit-jupiter:${rootProject.property("testcontainersVersion")}")
    testImplementation("org.testcontainers:mongodb:${rootProject.property("testcontainersVersion")}")
}
