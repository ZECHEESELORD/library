sourceSets {
    main {
        java.srcDir("../message-fabric/src/main/java")
        java.exclude("sh/harold/creative/library/message/fabric/FabricMessageSender.java")
    }
}

dependencies {
    api(project(":common:message-api"))
    implementation(project(":common:message-core"))
    minecraft("com.mojang:minecraft:${rootProject.property("fabric12111MinecraftVersion")}")
    add("mappings", loom.officialMojangMappings())
    add("modImplementation", "net.fabricmc:fabric-loader:${rootProject.property("fabric12111LoaderVersion")}")
    add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${rootProject.property("fabric12111ApiVersion")}")
    implementation("net.kyori:adventure-text-serializer-gson:${rootProject.property("adventureVersion")}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}
