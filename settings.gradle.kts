rootProject.name = "library"

include(
    ":common:camera-motion-api",
    ":common:camera-motion-core",
    ":common:data-api",
    ":common:data-memory",
    ":common:ui-values-api",
    ":common:screen-overlay-api",
    ":common:screen-overlay-core",
    ":common:message-api",
    ":common:message-core",
    ":common:sound-api",
    ":common:sound-core",
    ":common:state-machine-api",
    ":common:state-machine-core",
    ":common:menu-api",
    ":common:menu-core",
    ":common:entity-api",
    ":common:entity-core",
    ":common:house-service-entity",
    ":platform:paper:message-paper",
    ":platform:paper:sound-paper",
    ":platform:paper:camera-motion-paper",
    ":platform:paper:screen-overlay-paper",
    ":platform:paper:menu-paper",
    ":platform:paper:paper-example",
    ":platform:paper:entity-paper",
    ":platform:paper:entity-paper-citizens",
    ":platform:paper:paper-entity-example",
    ":platform:minestom:message-minestom",
    ":platform:minestom:sound-minestom",
    ":platform:minestom:camera-motion-minestom",
    ":platform:minestom:screen-overlay-minestom",
    ":platform:minestom:menu-minestom",
    ":platform:minestom:minestom-example",
    ":platform:minestom:entity-minestom",
    ":platform:minestom:minestom-entity-example",
    ":platform:velocity:message-velocity",
    ":platform:velocity:velocity-example",
)

dependencyResolutionManagement {
    repositoriesMode.set(org.gradle.api.initialization.resolve.RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.citizensnpcs.co/repo")
        maven("https://repo.minebench.de")
    }
}
