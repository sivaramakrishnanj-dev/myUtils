plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.sivarj.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.sivarj.assistant"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        ndk {
            // Modern phones only; skips x86 and 32-bit ARM native builds.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                // Whisper at -O0 is 10-50x slower than real-time, so force an
                // optimized native build even for the app's debug variant, and
                // enable fp16 vector arithmetic (huge ggml speedup on modern ARM).
                arguments += listOf(
                    "-DANDROID_ARM_NEON=ON",
                    "-DCMAKE_BUILD_TYPE=Release",
                )
                cFlags += listOf("-O3", "-march=armv8.2-a+fp16")
                cppFlags += listOf("-O3", "-march=armv8.2-a+fp16")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
