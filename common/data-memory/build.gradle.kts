dependencies {
    api(project(":common:data-api"))
    implementation("com.github.ben-manes.caffeine:caffeine:${rootProject.property("caffeineVersion")}")
}
