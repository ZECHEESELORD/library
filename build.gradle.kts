import org.gradle.api.JavaVersion
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
    id("net.fabricmc.fabric-loom-remap") apply false
}

val isJitPackBuild = System.getenv("JITPACK")?.equals("true", ignoreCase = true) == true
val configuredGroup = property("group").toString()
val configuredVersion = property("version").toString()
val jitPackGroup = System.getenv("GROUP")
    ?.takeUnless(String::isBlank)
    ?.let { "$it.${rootProject.name}" }
val jitPackVersion = System.getenv("VERSION")?.takeUnless(String::isBlank)
val bomProjectPath = ":library-bom"
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
val publishedLibraryProjectPaths = subprojects
    .filter { it.buildFile.isFile }
    .map { it.path }
    .filter { it != bomProjectPath && it !in unpublishedProjectPaths }

extra["publishedLibraryProjectPaths"] = publishedLibraryProjectPaths

val paperLatestProjectPaths = listOf(
    ":platform:paper:message-paper",
    ":platform:paper:sound-paper",
    ":platform:paper:camera-motion-paper",
    ":platform:paper:block-grid-paper",
    ":platform:paper:block-boundary-paper",
    ":platform:paper:screen-overlay-paper",
    ":platform:paper:scoreboard-paper",
    ":platform:paper:telegraph-paper",
    ":platform:paper:trajectory-preview-paper",
    ":platform:paper:impulse-paper",
    ":platform:paper:ambient-zone-paper",
    ":platform:paper:data-paper",
    ":platform:paper:paper-data-owner",
    ":platform:paper:menu-paper",
    ":platform:paper:paper-example",
    ":platform:paper:entity-paper",
    ":platform:paper:entity-paper-citizens",
    ":platform:paper:paper-entity-example",
)
val paperLegacy12111ProjectPaths = listOf(
    ":platform:paper:message-paper-1_21_11",
    ":platform:paper:sound-paper-1_21_11",
    ":platform:paper:camera-motion-paper-1_21_11",
    ":platform:paper:block-grid-paper-1_21_11",
    ":platform:paper:block-boundary-paper-1_21_11",
    ":platform:paper:screen-overlay-paper-1_21_11",
    ":platform:paper:scoreboard-paper-1_21_11",
    ":platform:paper:telegraph-paper-1_21_11",
    ":platform:paper:trajectory-preview-paper-1_21_11",
    ":platform:paper:impulse-paper-1_21_11",
    ":platform:paper:ambient-zone-paper-1_21_11",
    ":platform:paper:data-paper-1_21_11",
    ":platform:paper:menu-paper-1_21_11",
    ":platform:paper:entity-paper-1_21_11",
    ":platform:paper:entity-paper-citizens-1_21_11",
)

// JitPack serves multi-module repos under com.github.<owner>.<repo>.
group = if (isJitPackBuild && jitPackGroup != null) jitPackGroup else configuredGroup
version = if (isJitPackBuild && jitPackVersion != null) jitPackVersion else configuredVersion

subprojects {
    group = rootProject.group
    version = rootProject.version

    if (path == bomProjectPath) {
        return@subprojects
    }

    apply<JavaLibraryPlugin>()
    val isLegacyFabric12111 = path.startsWith(":platform:fabric:") && name.endsWith("-1_21_11")
    val isLegacyPaper12111 = path.startsWith(":platform:paper:") && name.endsWith("-1_21_11")
    val isLatestPaper = path.startsWith(":platform:paper:") && !isLegacyPaper12111
    if (isLegacyFabric12111) {
        apply(plugin = "net.fabricmc.fabric-loom-remap")
    } else if (path.startsWith(":platform:fabric:")) {
        apply(plugin = "net.fabricmc.fabric-loom")
    }

    val targetJava = when {
        isLegacyFabric12111 || isLegacyPaper12111 -> 21
        isLatestPaper || path.startsWith(":platform:minestom:") || path.startsWith(":platform:fabric:") -> 25
        else -> 21
    }
    val javaExtension = extensions.getByType<JavaPluginExtension>()
    javaExtension.sourceCompatibility = JavaVersion.toVersion(targetJava)
    javaExtension.targetCompatibility = JavaVersion.toVersion(targetJava)

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
        if (targetJava >= 25) {
            jvmArgs("-Dnet.bytebuddy.experimental=true", "-XX:+EnableDynamicAgentLoading")
        }
    }

    if (path in publishedLibraryProjectPaths) {
        apply(plugin = "maven-publish")
        javaExtension.withSourcesJar()

        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                    artifactId = project.name
                    pom {
                        licenses {
                            license {
                                name.set("GNU General Public License v3.0 only")
                                url.set("https://www.gnu.org/licenses/gpl-3.0-standalone.html")
                                distribution.set("repo")
                            }
                        }
                    }
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
tasks.register("paperLatestCheck") {
    group = "verification"
    description = "Runs checks for the latest Paper adapter lane."
    dependsOn(paperLatestProjectPaths.map { "$it:check" })
}

tasks.register("paperLegacy12111Check") {
    group = "verification"
    description = "Runs checks for the Paper 1.21.11 legacy adapter lane."
    dependsOn(paperLegacy12111ProjectPaths.map { "$it:check" })
}

tasks.register("paperCompatibilityCheck") {
    group = "verification"
    description = "Runs both latest and Paper 1.21.11 adapter checks."
    dependsOn("paperLatestCheck", "paperLegacy12111Check")
}
