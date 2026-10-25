plugins {
    id("com.android.application")
    // AGP 9 já traz Kotlin embutido: NÃO aplicar org.jetbrains.kotlin.android.
    // Compose agora usa este plugin no lugar de composeOptions.
    id("org.jetbrains.kotlin.plugin.compose")
}

// O visualizador .milk usa código nativo (projectM). Só liga o build nativo se o
// código-fonte do projectM estiver em app/src/main/cpp/projectm (ver cpp/README.md).
val hasProjectM = file("src/main/cpp/projectm/CMakeLists.txt").exists()

android {
    namespace = "com.tolete.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "gui.pgcr.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 210
        versionName = "0.50"

        if (hasProjectM) {
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        }
    }

    if (hasProjectM) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))

    implementation("androidx.core:core-ktx:1.13.1")
    // Necessário para o tema Theme.Material3.* em res/values/themes.xml
    implementation("com.google.android.material:material:1.11.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("androidx.palette:palette-ktx:1.0.0")

    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Locais em app/libs — listados uma vez só (evita D8 duplicate classes)
    implementation(files(
        "libs/jaudiotagger-3.0.1.jar",
        "libs/lib-decoder-ffmpeg-release.aar"
    ))
}
