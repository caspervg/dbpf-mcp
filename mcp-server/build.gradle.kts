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
    implementation(libs.toolSchema)
    // The MCP SDK declares its ktor dependencies without versions, so this BOM is what makes them
    // resolvable. It is not optional even though nothing here imports ktor directly.
    implementation(platform(libs.ktorBom))
    runtimeOnly(libs.slf4jSimple)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}

application {
    mainClass = "com.github.caspervg.dbpfmcp.server.MainKt"
}
