dependencies {
    api(project(":common:data-core"))
    implementation("org.yaml:snakeyaml:${rootProject.property("snakeyamlVersion")}")
}
