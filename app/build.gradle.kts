plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.kinoshka.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kinoshka.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    // Один ключ для всех сборок, чтобы обновления ставились поверх.
    // В репозитории ключ лежит только зашифрованным (kinoshka.keystore.enc);
    // GitHub Actions расшифровывает его паролем из секрета SIGNING_KEY_PASS.
    val keystore = file("kinoshka.keystore")
    signingConfigs {
        if (keystore.exists()) {
            create("kinoshka") {
                storeFile = keystore
                storePassword = "kinoshka123"
                keyAlias = "kinoshka"
                keyPassword = "kinoshka123"
            }
        }
    }

    buildTypes {
        val kino = signingConfigs.findByName("kinoshka")
        debug {
            if (kino != null) signingConfig = kino
        }
        release {
            isMinifyEnabled = false
            signingConfig = kino ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/README.md",
                "META-INF/CHANGES",
                "META-INF/COPYRIGHT",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
            )
        }
    }
}

dependencies {
    // Извлечение потоков YouTube (та же версия, что у NewPipe и «Волны»)
    implementation("com.github.TeamNewPipe:NewPipeExtractor:6cb36e982311dc1c529997bc329013afe75f37d1")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
