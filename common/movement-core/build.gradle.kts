dependencies {
    api(project(":common:movement-api"))
    api(project(":common:hot-reload-api"))
    api(project(":common:prediction-api"))
    implementation(project(":common:hot-reload-core"))
    implementation(project(":common:prediction-core"))
    testImplementation(project(":common:camera-core"))
}
