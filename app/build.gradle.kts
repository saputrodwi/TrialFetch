plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Kredensial signing. File keystore-nya sendiri bisa datang dari dua
// sumber: path (TF_STORE_FILE) ATAU isi base64 (TF_STORE_BASE64) — yang
// kedua adalah cara standarnya GitHub Actions menyimpan file biner,
// karena Actions hanya bisa menyimpan secret berbentuk teks.
val tfStoreFile = (System.getenv("TF_STORE_FILE") as String?)
    ?: (project.findProperty("TF_STORE_FILE") as String?)
val tfStoreBase64 = (System.getenv("TF_STORE_BASE64") as String?)
    ?: (project.findProperty("TF_STORE_BASE64") as String?)
val tfStorePassword = (System.getenv("TF_STORE_PASSWORD") as String?)
    ?: (project.findProperty("TF_STORE_PASSWORD") as String?)
val tfKeyAlias = (System.getenv("TF_KEY_ALIAS") as String?)
    ?: (project.findProperty("TF_KEY_ALIAS") as String?)
val tfKeyPassword = (System.getenv("TF_KEY_PASSWORD") as String?)
    ?: (project.findProperty("TF_KEY_PASSWORD") as String?)

// Syarat minimal: ada password, alias, dan password kunci. File keystore
// boleh datang lewat path maupun base64 — yang wajib ada salah satu.
val hasReleaseSigning = !tfStorePassword.isNullOrBlank() &&
    !tfKeyAlias.isNullOrBlank() &&
    !tfKeyPassword.isNullOrBlank() &&
    (!tfStoreFile.isNullOrBlank() || !tfStoreBase64.isNullOrBlank())

// Hasil decode base64 ditaruh di build/ (sudah masuk .gitignore) dan
// dihapus ulang oleh workflow pada langkah "Hapus material signing".
//
// Ekstensi bukan cosmetic: Gradle menentukan format keystore dari nama
// file. Isi JKS yang diberi ekstensi .p12 akan ditolak saat signing, dan
// sebaliknya. Karena itu ekstensinya ikut dikonfigurasi (default "jks",
// ubah ke "p12" kalau nanti kamu pakai PKCS#12).
val stagingDir = rootProject.layout.buildDirectory.dir("signing").get().asFile
val storeExt = ((System.getenv("TF_STORE_EXT") as String?)
    ?: (project.findProperty("TF_STORE_EXT") as String?)
    ?: "jks").trim().removePrefix(".").lowercase()

val releaseStoreFile: File? = when {
    !hasReleaseSigning -> null
    !tfStoreBase64.isNullOrBlank() -> {
        stagingDir.mkdirs()
        File(stagingDir, "keystore.$storeExt").apply {
            if (!exists()) {
                writeBytes(android.util.Base64.decode(tfStoreBase64, android.util.Base64.DEFAULT))
            }
        }
    }
    else -> rootProject.file(tfStoreFile!!)
}

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
        if (hasReleaseSigning && releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
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

    // Empat ABI + satu universal APK.
    //
    // Catatan: proyek ini tidak punya kode native (murni Kotlin/Java), jadi
    // secara teknis semua APK ini berisi kelas yang sama dan tidak ada
    // perbedaan ukuran nyata. Split tetap dikonfigurasi karena
    // perangkat lawas sering butuh paket per-ABI, dan universal dipakai
    // untuk-gitung/install yang praktis.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
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
