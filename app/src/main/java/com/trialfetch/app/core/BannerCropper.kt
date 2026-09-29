package com.trialfetch.app.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Pemotong banner/watermark Baozimh.
 *
 * BAOZIMH menyisipkan pita **200px** di atas gambar (banner "包子漫畫 /
 * www.baozimh.com"), dan di beberapa mirror pita 200px juga di bawah
 * (watermark "动漫之家 / www.dmzj.com"). Pita hanya ada pada sebagian
 * halaman, jadi tidak bisa dipotong buta — harus dideteksi dulu.
 *
 * CATATAN PENTING soal metode: template matching terhadap citra banner
 * (yang dipakai versi web lewat OpenCV) TIDAK dipakai di sini.ixaSudah
 * dicoba dan diukur: skor band banner hanya ~0.71 vs ambang 0.75, dan
 * tidak terpisah bersih dari panel komik (0.30-0.62) — karena isi teks
 * banner berubah-ubah tiap unggahan, sehingga pixel match ke template
 * statis rapuh. Yang dipakai malah heuristik struktural di bawah, yang
 * diukur memberi pemisahan jauh lebih baik (banner ~0.95, non-banner
 * ~0.00 pada 9 gambar sampel).
 *
 * Tiga ciri yang dipakai:
 *   1. Simetri kiri-kanan tinggi — logo + teks banner terletak di tengah.
 *   2. Tiga zone horizontal (atas/tengah/bawah) sama-sama BERJARANG —
 *      banner punya sedikit tinta, panel komik padat.
 *   3. Konten menumpuk di tengah; dua sisi tepi nyaris kosong.
 */
object BannerCropper {

    /** Tinggi pita banner, sesuai template banner_*.jpg (tinggi tepat 200). */
    const val BANNER_HEIGHT = 200

    private const val GRAY_THRESHOLD = 240
    private const val ZONE_THRESHOLD_MID = 140
    private const val MIN_SYMMETRY = 0.30f

    private const val ZONE_TOP_MIN = 0.01f
    private const val ZONE_TOP_MAX = 0.15f
    private const val ZONE_MID_MIN = 0.02f
    private const val ZONE_MID_MAX = 0.20f

    private const val EDGE_MAX_CONTENT = 0.05f
    private const val CENTER_MIN_CONTENT = 0.03f

    /** Ambang "ini banner". Pemisahan hasil uji: 0.95 (banner) vs 0.00 (bukan). */
    private const val SCORE_THRESHOLD = 0.75f

    private const val EDGE_FRACTION = 0.25f
    private const val CENTER_FRACTION = 0.50f

    data class Result(
        val hasTop: Boolean,
        val hasBottom: Boolean,
        val topScore: Float,
        val bottomScore: Float
    ) {
        val isChanged: Boolean get() = hasTop || hasBottom
    }

    fun detect(bitmap: Bitmap): Result {
        val h = bitmap.height
        if (h <= BANNER_HEIGHT) return Result(false, false, 0f, 0f)
        val top = scoreRegion(bitmap, 0)
        val bottom = scoreRegion(bitmap, h - BANNER_HEIGHT)
        return Result(
            hasTop = top >= SCORE_THRESHOLD,
            hasBottom = bottom >= SCORE_THRESHOLD,
            topScore = top,
            bottomScore = bottom
        )
    }

    /** Deteksi + potong. Null kalau tidak ada yang perlu dipotong. */
    fun crop(bytes: ByteArray, format: ImageFormat): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val result = detect(bitmap)
        if (!result.isChanged) {
            bitmap.recycle()
            return null
        }
        val top = if (result.hasTop) BANNER_HEIGHT else 0
        val bottom = if (result.hasBottom) BANNER_HEIGHT else 0
        val newHeight = bitmap.height - top - bottom
        if (newHeight <= 0) {
            bitmap.recycle()
            return null
        }
        val cropped = Bitmap.createBitmap(bitmap, 0, top, bitmap.width, newHeight)
        bitmap.recycle()

        val out = ByteArrayOutputStream()
        val compress = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
        cropped.compress(compress, if (format == ImageFormat.PNG) 100 else 95, out)
        cropped.recycle()
        return out.toByteArray()
    }

    /** Skor 0..1 untuk pita 200px mulai di baris [startY]; 0 = bukan banner. */
    private fun scoreRegion(bitmap: Bitmap, startY: Int): Float {
        val w = bitmap.width
        val h = BANNER_HEIGHT
        if (w < 100 || startY + h > bitmap.height) return 0f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, startY, w, h)
        val lum = IntArray(pixels.size) { i ->
            val c = pixels[i]
            (0.299 * (c shr 16 and 0xFF) +
                0.587 * (c shr 8 and 0xFF) +
                0.114 * (c and 0xFF)).toInt()
        }

        // (1) Simetri kiri-kanan.
        val half = w / 2
        if (half <= 0) return 0f
        var symDiff = 0L
        for (y in 0 until h) {
            val base = y * w
            for (x in 0 until half) {
                symDiff += abs(lum[base + x] - lum[base + w - 1 - x]).toLong()
            }
        }
        val symmetry = (1f - (symDiff.toFloat() / (half * h)) / 255f).coerceIn(0f, 1f)
        if (symmetry < MIN_SYMMETRY) return 0f

        // (2) Ketiga zone horizontal harus jarang.
        val third = h / 3
        if (third <= 0) return 0f
        val zones = listOf(
            Triple(0, third, GRAY_THRESHOLD),
            Triple(third, third, ZONE_THRESHOLD_MID),
            Triple(2 * third, h - 2 * third, GRAY_THRESHOLD)
        )
        val ranges = listOf(
            ZONE_TOP_MIN to ZONE_TOP_MAX,
            ZONE_MID_MIN to ZONE_MID_MAX,
            ZONE_TOP_MIN to ZONE_TOP_MAX
        )
        for ((i, z) in zones.withIndex()) {
            val ratio = darkRatio(lum, w, 0, w, z.third, z.second, z.first)
            val (lo, hi) = ranges[i]
            if (ratio < lo || ratio > hi) return 0f
        }

        // (3) Konten di tengah, tepi nyaris kosong.
        val centerW = (w * CENTER_FRACTION).toInt()
        val edgeW = (w * EDGE_FRACTION).toInt()
        val centerX = (w - centerW) / 2
        val centerContent = darkRatio(lum, w, centerX, centerW, GRAY_THRESHOLD, h)
        val leftContent = darkRatio(lum, w, 0, edgeW, GRAY_THRESHOLD, h)
        val rightContent = darkRatio(lum, w, w - edgeW, edgeW, GRAY_THRESHOLD, h)
        if (!(leftContent < EDGE_MAX_CONTENT && rightContent < EDGE_MAX_CONTENT &&
                centerContent > CENTER_MIN_CONTENT)
        ) return 0f

        val whiteRatio = 1f - darkRatio(lum, w, 0, w, GRAY_THRESHOLD, h)
        return (whiteRatio * 0.25f + symmetry * 0.20f + 0.55f).coerceIn(0f, 1f)
    }

    /** Porsi piksel lebih gelap dari [threshold] di area [xOffset, +areaWidth). */
    private fun darkRatio(
        lum: IntArray, stride: Int,
        xOffset: Int, areaWidth: Int, threshold: Int, areaHeight: Int,
        startRow: Int = 0
    ): Float {
        if (areaWidth <= 0 || areaHeight <= 0) return 0f
        val x0 = xOffset.coerceAtLeast(0)
        val x1 = (xOffset + areaWidth).coerceAtMost(stride)
        if (x1 <= x0) return 0f
        var dark = 0
        for (y in startRow until (startRow + areaHeight)) {
            if (y < 0 || y >= areaHeight) continue
            val rowBase = y * stride
            for (x in x0 until x1) {
                if (lum[rowBase + x] < threshold) dark++
            }
        }
        return dark.toFloat() / ((x1 - x0).toLong() * areaHeight).toFloat()
    }
}
