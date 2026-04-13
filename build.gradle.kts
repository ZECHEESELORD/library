import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

plugins {
    base
    id("net.fabricmc.fabric-loom") apply false
}

val isJitPackBuild = System.getenv("JITPACK")?.equals("true", ignoreCase = true) == true
val configuredGroup = property("group").toString()
val configuredVersion = property("version").toString()
val jitPackGroup = System.getenv("GROUP")
    ?.takeUnless(String::isBlank)
    ?.let { "$it.${rootProject.name}" }
val jitPackVersion = System.getenv("VERSION")?.takeUnless(String::isBlank)
val unpublishedProjectPaths = setOf(
    ":platform:paper:paper-example",
    ":platform:paper:paper-entity-example",
    ":platform:paper:paper-data-owner",
    ":platform:minestom:minestom-example",
    ":platform:minestom:minestom-entity-example",
    ":platform:minestom:minestom-data-owner",
    ":platform:velocity:velocity-data-owner",
    ":platform:velocity:velocity-example",
    ":platform:fabric:fabric-example",
    ":platform:fabric:fabric-client-example",
)

// JitPack serves multi-module repos under com.github.<owner>.<repo>.
group = if (isJitPackBuild && jitPackGroup != null) jitPackGroup else configuredGroup
version = if (isJitPackBuild && jitPackVersion != null) jitPackVersion else configuredVersion

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply<JavaLibraryPlugin>()
    if (path.startsWith(":platform:fabric:")) {
        apply(plugin = "net.fabricmc.fabric-loom")
    }

    val targetJava = if (path.startsWith(":platform:minestom:") || path.startsWith(":platform:fabric:")) 25 else 21
    val javaExtension = extensions.getByType<JavaPluginExtension>()

    // JitPack runs the build on one configured JDK; use --release for mixed targets there.
    if (!isJitPackBuild) {
        javaExtension.toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(targetJava)
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:${rootProject.property("junitVersion")}"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    if (path !in unpublishedProjectPaths) {
        apply(plugin = "maven-publish")
        javaExtension.withSourcesJar()

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.name
                }
            }
        }
    }
}

tasks.register("runMinestomExample") {
    group = "application"
    description = "Runs the embedded Minestom dev harness."
    dependsOn(":platform:minestom:minestom-example:run")
}

tasks.register("runFabricServerExample") {
    group = "application"
    description = "Runs the Fabric server example using Loom."
    dependsOn(":platform:fabric:fabric-example:runServer")
}

tasks.register("runFabricClientExample") {
    group = "application"
    description = "Runs the Fabric client example using Loom."
    dependsOn(":platform:fabric:fabric-client-example:runClient")
}
