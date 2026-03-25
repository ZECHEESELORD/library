rootProject.name = "library"

include(
    ":common:data-api",
    ":common:data-memory",
    ":common:message-api",
    ":common:message-core",
    ":common:state-machine-api",
    ":common:state-machine-core",
    ":common:menu-api",
    ":common:menu-core",
    ":platform:paper:message-paper",
    ":platform:paper:menu-paper",
    ":platform:paper:paper-example",
    ":platform:minestom:message-minestom",
    ":platform:minestom:menu-minestom",
    ":platform:minestom:minestom-example",
    ":platform:velocity:message-velocity",
    ":platform:velocity:velocity-example",
)

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
