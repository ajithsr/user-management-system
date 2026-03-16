plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)

            implementation(libs.sqldelight.coroutines.extensions)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime.compose)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidInstrumentedTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.androidx.test.runner)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.android)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.koin.android)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.native.driver)
        }
    }
}

android {
    namespace = "com.sliide.usermanagement"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

composeCompiler {
    // Teach the Compose compiler that kotlinx.datetime.Instant is immutable.
    // Without this, any data class containing an Instant field is inferred as
    // unstable, disabling LazyColumn item-skip for User and UserFeedItem.
    stabilityConfigurationFile = project.file("compose_compiler_config.conf")
}

// androidx.lifecycle:lifecycle-runtime-compose has no iOS KMP artifacts.
// Substitute it everywhere with the JetBrains fork, which ships iOS variants
// and is already bundled transitively by Compose Multiplatform 1.7.3.
// This also covers the transitive pulls from lifecycle-viewmodel-iosarm64,
// lifecycle-common-iosarm64, and lifecycle-runtime-iosarm64.
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute(module("androidx.lifecycle:lifecycle-runtime-compose"))
            .using(module("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4"))
    }
}

sqldelight {
    databases {
        create("UserDatabase") {
            packageName.set("com.sliide.usermanagement.data.local.db")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.1.0")
        }
    }
}
