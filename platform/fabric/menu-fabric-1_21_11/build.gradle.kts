sourceSets {
    main {
        java.srcDir("src/main/java")
    }
}

dependencies {
    api(project(":common:menu-api"))
    implementation(project(":common:menu-core"))
    implementation(project(":platform:fabric:sound-fabric-1_21_11"))
    minecraft("com.mojang:minecraft:${rootProject.property("fabric12111MinecraftVersion")}")
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("fabric12111LoaderVersion")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric12111ApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
    implementation("net.kyori:adventure-text-serializer-plain:${rootProject.property("adventureVersion")}")
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
