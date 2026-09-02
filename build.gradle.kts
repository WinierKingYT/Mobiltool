plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(17)
        }
    }
    plugins.withId("org.jetbrains.kotlin.android") {
        configure<org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension> {
            jvmToolchain(17)
        }
    }
    tasks.withType<Test>().configureEach {
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US")
        val shortRoot = rootDir.absolutePath.replace("aramakayıt", "ARAMAK~1")
        if (shortRoot != rootDir.absolutePath) {
            doFirst {
                testClassesDirs = files(testClassesDirs.files.map { File(it.absolutePath.replace("aramakayıt", "ARAMAK~1")) })
                classpath = files(classpath.files.map { File(it.absolutePath.replace("aramakayıt", "ARAMAK~1")) })
            }
        }
    }
}
