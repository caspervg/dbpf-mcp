plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

tasks.named<ProcessResources>("processResources") {
    from(rootProject.file("vendor/sc4-properties/tropod_Properties.xml"))
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.bundles.common)
    implementation(libs.ksoup)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}
