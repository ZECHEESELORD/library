dependencies {
    api(project(":common:camera-api"))
    api(project(":common:tween-core"))
    api(project(":common:telemetry-api"))
    api(project(":common:hot-reload-api"))
    testImplementation(project(":common:telemetry-core"))
    testImplementation(project(":common:hot-reload-core"))
}
