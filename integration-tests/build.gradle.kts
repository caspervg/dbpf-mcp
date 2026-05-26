plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":sc4-semantics"))
    implementation(project(":backend-scdbpf"))
    implementation(project(":mcp-server"))
    implementation(libs.bundles.common)
    implementation(libs.scdbpf)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}
