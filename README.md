# TrialFetch

Aplikasi Android native (Kotlin + Jetpack Compose) untuk mencari dan
mengunduh komik dari berbagai sumber, lalu menyimpannya ke folder
`Download/TrialFetch` sebagai folder gambar atau arsip ZIP.

Aplikasi pendamping dari Trial Fetch versi web — logika scraping,
API app, dan pemotong banner diport dari sana supaya hasilnya identik.

## Sumber

| Sumber | Cara ambil data |
|---|---|
| Baozimh | API app (`appgb*.baozimh.com`) + fallback scrape `twmanga.com` |
| Manwang | Scrape + dekripsi AES params |
| Wmanhua | Scrape HTML |
| Koudaimh | Scrape HTML (tanpa Referer) |
| Jjabtoon | REST JSON (`/api/webtoons`, `/api/episodes`) |
| Jjaptoon | Scrape HTML (domain bernomor) |
| Goodtoon | WordPress Madara (AJAX + `data-src`) |
| Rumanhua | Seperti Manwang (backend & kunci sama) |

## Fitur

- Pencarian per sumber + tempel URL langsung (mendukung semua sumber)
- Bookmark series (tersimpan permanen di HP)
- Unduh chapter ke folder atau ZIP, penamaan `0001`, `001`, `page_0001`, …
- Pemotong banner Baozimh 200px (OpenCV template matching + deteksi watermark samar)
- Tema Terang / Gelap / Ikuti sistem, menu pengaturan tersimpan permanen
- Notifikasi progres + ringkasan banner per chapter

## Build

CI (GitHub Actions) membangun APK debug dan release untuk
`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`, plus universal.
Sengaja memakai Gradle dari runner (`gradle/actions/setup-gradle`), bukan
`./gradlew`, supaya repo tidak menyimpan `gradle-wrapper.jar`.

Kebutuhan: JDK 17, Gradle 8.9 (atau biarkan CI yang build).

```sh
gradle :app:assembleDebug
```

APK release butuh signing. Isi secret berikut di repo
(Settings > Secrets > Actions), keystore didekode ke `build/signing/`
lalu dihapus otomatis setiap build:

- `TF_STORE_BASE64` — keystore JKS dalam base64
- `TF_STORE_EXT` — `jks`
- `TF_STORE_PASSWORD`, `TF_KEY_ALIAS`, `TF_KEY_PASSWORD`

## Menambah sumber baru

1. Buat `XxxSource(private val http: HttpClient) : ComicSource`
   di `app/src/main/java/com/trialfetch/app/core/`.
2. Implementasikan `search()`, `series()`, `chapter()`. Untuk error yang
   aman ditampilkan, lempar `SourceException`.
3. Daftarkan di `Source` (`Models.kt`), di map `sources`
   (`ComicRepository.kt`), dan pola URL-nya di `UrlParser.kt`.
4. Sumber muncul otomatis di daftar pencarian dan tempel URL.

## Struktur

- `core/` — sumber komik, HTTP, parsing, dekripsi, cropper banner
- `data/` — repository, MediaStore writer, settings/bookmark store, notifikasi
- `ui/` — screen Compose, tema, permission
- `res/` — font (Baloo 2, Quicksand, subset Latin), template banner,
  trust anchor tambahan (`network_security_config`)

## Catatan

- Gambar Manwang/Rumanhua `source_id` 12 terenkripsi AES-128-CBC dan
  dibuka saat unduh (kunci bersama karena backend sama).
- `twmanga.com` memakai rantai ke root Sectigo R46 (2021); perangkat
  dengan store CA usang dibantu via trust anchor di `res/raw`.
- Logo watermark sudut (mis. Tencent) menempel di dalam file gambar
  sumber dan tidak bisa dihapus dengan memotong.
