plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
}

tasks.test {
    // P2-DIRECT-FINAL-04: Standard test task is 100% deterministic and offline
    exclude("**/*RealPublicDirectMediaTest*")
    exclude("**/*RealNetworkTest*")
}

tasks.register<Test>("realDirectMediaTest") {
    description = "Runs the explicit opt-in live public direct media test"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    include("**/*RealPublicDirectMediaTest*")
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed", "standardOut", "standardError")
    }
}
