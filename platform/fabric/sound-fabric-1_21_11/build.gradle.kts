sourceSets {
    main {
        java.srcDir("../sound-fabric/src/main/java")
    }
}

dependencies {
    api(project(":common:sound-api"))
    implementation(project(":common:sound-core"))
    minecraft("com.mojang:minecraft:${rootProject.property("fabric12111MinecraftVersion")}")
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("fabric12111LoaderVersion")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric12111ApiVersion")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

tasks.named<org.gradle.jvm.tasks.Jar>("jar") {
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveClassifier.set("")
}

tasks.named("remapJar") {
    enabled = false
}
