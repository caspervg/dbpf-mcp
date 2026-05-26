plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(libs.bundles.common)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}
