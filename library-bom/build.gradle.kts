import org.gradle.api.publish.maven.MavenPublication

plugins {
    `java-platform`
    `maven-publish`
}

@Suppress("UNCHECKED_CAST")
val publishedLibraryProjectPaths = rootProject.extra["publishedLibraryProjectPaths"] as List<String>

dependencies {
    constraints {
        publishedLibraryProjectPaths.forEach { projectPath ->
            api(project(projectPath))
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenBom") {
            from(components["javaPlatform"])
            artifactId = "library-bom"
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
