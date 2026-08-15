plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

// The SC4 property registry comes from the vendor/sc4-properties submodule. Gradle's `from()`
// silently ignores a missing file, so without this check a clone made without
// --recurse-submodules produces a green build and a jar with no registry; the failure then
// surfaces only at runtime, on the first property lookup, as an opaque tool error.
val propertyRegistryFile = rootProject.file("vendor/sc4-properties/tropod_Properties.xml")

// Resolved at configuration time; referencing the project inside the task action would make the
// task incompatible with the configuration cache.
val missingRegistryMessage =
    """
    Missing ${propertyRegistryFile.relativeTo(rootProject.projectDir)}.
    The SC4 property registry is a git submodule. Fetch it with:

        git submodule update --init --recursive
    """.trimIndent()

// A separate task rather than a doFirst on processResources: with the registry absent that task
// has no inputs at all, so Gradle skips it as NO-SOURCE and any action attached to it never runs
// — which is precisely how a missing submodule used to produce a green build.
val verifyPropertyRegistry by tasks.registering {
    val registryFile = propertyRegistryFile
    val message = missingRegistryMessage
    doLast {
        if (!registryFile.exists()) {
            throw GradleException(message)
        }
    }
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(verifyPropertyRegistry)
    from(propertyRegistryFile)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.bundles.common)
    implementation(libs.ksoup)
    testImplementation(platform(libs.junitBom))
    testImplementation(libs.junitJupiter)
    testImplementation(kotlin("test"))
}
