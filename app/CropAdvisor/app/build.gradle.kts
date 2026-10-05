plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cropadvisor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cropadvisor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        ndk { abiFilters += listOf("arm64-v8a") }   // Arm phones only (keeps the app small)
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    androidResources { noCompress += listOf("pte") }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += "**/libc++_shared.so"
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Declared FIRST on purpose: fbjni ships the newer libc++_shared.so that ExecuTorch needs.
    // If OpenCV's older copy is picked instead, the app dies with "cannot locate symbol ... basic_stringstream".
    implementation("com.facebook.fbjni:fbjni:0.7.0")
    implementation("com.facebook.soloader:soloader:0.10.5")
    implementation("org.pytorch:executorch-android:1.5.1")   // on-device runtime
    implementation("org.opencv:opencv:4.9.0")                 // leaf damage measurement
}
