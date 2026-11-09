plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.example.dawnalarm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.dawnalarm"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    // Signing with a permanent key. The GitHub build sets KEYSTORE_PATH and the passwords only
    // when the signing secrets exist. Without them, the build uses the temporary debug key.
    val keystorePath: String? = System.getenv("KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            create("dawn") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DAWN_STORE_PASSWORD")
                keyAlias = "dawnalarm"
                keyPassword = System.getenv("DAWN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("dawn")
            }
        }
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("dawn")
            }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
