dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "dbpf-mcp"
include(":core-api")
include(":sc4-semantics")
include(":backend-scdbpf")
include(":mcp-server")
include(":integration-tests")
