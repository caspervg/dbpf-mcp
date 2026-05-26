plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
    application
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":sc4-semantics"))
    implementation(project(":backend-scdbpf"))
    implementation(libs.bundles.common)
    implementation(libs.kotlinReflect)
    implementation(libs.mcpKotlinSdkServer)
    implementation(platform("io.ktor:ktor-bom:3.2.3"))
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-sse")
    runtimeOnly(libs.slf4jSimple)
    runtimeOnly("io.ktor:ktor-server-websockets")
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}

application {
    mainClass = "com.github.caspervg.dbpfmcp.server.MainKt"
}
