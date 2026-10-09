# Aturan R8 untuk build release (isMinifyEnabled = true).
#
# Prinsipnya: JANGAN keep kode aplikasi sendiri — biarkan R8 memangkasnya.
# Yang di-keep hanya bagian yang namanya dipakai di luar Kotlin/Java
# (JNI, reflection pihak ketiga, atau mekanisme platform).

# --- OpenCV -------------------------------------------------------------
# org.opencv memanggil kode native lewat JNI: nama kelas + metode native
# harus identik dengan yang ada di .so. Kalau di-rename, initLocal() atau
# pemanggilan cv.* gagal di runtime hanya pada APK release.
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# --- OkHttp / Okio ------------------------------------------------------
# OkHttp sendiri sudah menyertakan consumer rules; di sini hanya peredam
# peringatan kelas opsional (Conscrypt/BouncyCastle/OpenJSSE) yang memang
# tidak ikut dibawa — dipakai lewat try/catch, bukan wajib ada.
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Stacktrace yang masih terbaca -------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Kelas opsional yang tidak ikut dibawa -----------------------------
# OkHttp/Okio menulis anotasi JetBrains/IntelliJ ke bytecode dan OkHttp
# memilih platform TLS lewat Class.forName(...) di dalam try/catch. R8
# menganggap kelas yang tidak ditemukan jadi error di AGP 8, bukan
# sekadar warning — daftar di sini supaya build release tetap hijau.
-dontwarn org.jetbrains.annotations.**
-dontwarn org.intellij.lang.annotations.**
-dontwarn javax.annotation.meta.**
-dontwarn java.lang.invoke.**

# Anotasi runtime dipakai Compose & lint untuk membaca metadata; tanpa ini
# sebagian kecil anotasi hilang dari APK release (sudah juga ditulis di atas,
# dipertahankan di sini sebagai jaring pengaman agar urutan atribut jelas).
-keepattributes InnerClasses,EnclosingMethod
