import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.api.plugins.JavaPluginExtension

plugins {
    base
}

group = property("group").toString()
version = property("version").toString()

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply<JavaLibraryPlugin>()

    val targetJava = if (path.startsWith(":platform:minestom:")) 25 else 21

    extensions.getByType<JavaPluginExtension>().toolchain.languageVersion.set(JavaLanguageVersion.of(targetJava))

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
}

tasks.register("runMinestomExample") {
    group = "application"
    description = "Runs the embedded Minestom dev harness."
    dependsOn(":platform:minestom:minestom-example:run")
}
