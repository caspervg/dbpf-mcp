plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":sc4-semantics"))
    implementation(libs.kotlinLogging)
    implementation(libs.scdbpf)
    implementation(libs.kotlinxSerialization)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}
