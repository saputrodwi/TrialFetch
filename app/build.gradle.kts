plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Keystore dibaca dari gradle.properties (values) atau environment (CI),
// supaya tidak pernah ikut ter-commit.
val tfStoreFile = (project.findProperty("TF_STORE_FILE") as String?)
    ?: System.getenv("TF_STORE_FILE")
val tfStorePassword = (project.findProperty("TF_STORE_PASSWORD") as String?)
    ?: System.getenv("TF_STORE_PASSWORD")
val tfKeyAlias = (project.findProperty("TF_KEY_ALIAS") as String?)
    ?: System.getenv("TF_KEY_ALIAS")
val tfKeyPassword = (project.findProperty("TF_KEY_PASSWORD") as String?)
    ?: System.getenv("TF_KEY_PASSWORD")

val hasReleaseSigning = listOf(tfStoreFile, tfStorePassword, tfKeyAlias, tfKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "com.trialfetch.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.trialfetch.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(tfStoreFile!!)
                storePassword = tfStorePassword
                keyAlias = tfKeyAlias
                keyPassword = tfKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Kalau signing belum disiapkan, jatuh ke debug key supaya
            // `assembleRelease` tetap menghasilkan APK yang bisa di-install.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.ui.tooling)
}
