package com.trialfetch.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.trialfetch.app.R
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pemotong banner/watermark Baozimh.
 *
 * Ini **port** dari mesin yang dipakai Trial Fetch web (index.html,
 * fungsi maybeCropBanner / bannerCheckRegion / bannerScoreRegion), dengan
 * OpenCV asli — sama seperti web memakai opencv.js. Calls OpenCV dipakai
 * langsung (threshold, dilate, erode, GaussianBlur, Canny, equalizeHist,
 * resize, matchTemplate) supaya hasilnya sama persis dengan acuan.
 * Versi sebelumnya mengimplementasikan operasi itu sendiri dan skornya
 * tidak cocok, sehingga banner bocor.
 *
 * Pita banner Baozimh setinggi 200px dan bisa muncul di atas atau di bawah.
 * Pengukuran pixel pada chapter 19 menunjukkan ada dua varian:
 *
 *  A. Banner gelap — pita putih sekitar 0.91 dengan teks pekat di tengah.
 *     Ditangani template matching terhadap 4 template.
 *
 *  B. Watermark samar — pita putih sekitar 0.987 yang isinya HANYA abu
 *     terang 209-249, tanpa satu pun piksel hitam, kontennya di kanan.
 *     Varian ini mustahil dikenali template matching, jadi punya aturan
 *     terpisah berbasis rasio piksel.
 *
 * Kata "template" di sini tidak berarti pixel harus sama persis: isi teks
 * banner berubah tiap unggahan, jadi yang dibandingkan adalah struktur citra.
 */
class BannerCropper(private val context: Context) {

    private class Template(
        val raw: Mat,
        val gray: Mat,
        val edge: Mat,
        val dark: Mat,
        val mask: Mat,
        val width: Int,
        val edgePixels: Int,
        val darkPixels: Int
    )

    private companion object {
        const val BANNER_HEIGHT = 200
        const val WIDTH_MATCH_TOLERANCE = 0.25
        const val MATCH_THRESHOLD = 0.58

        const val W_MASK = 0.25
        const val W_GRAY = 0.30
        const val W_EDGE = 0.18
        const val W_DARK = 0.25
        const val W_DIFF = 0.02

        const val DARK_THRESHOLD = 135
        const val MASK_THRESHOLD = 246
        const val CANNY_LOW = 45
        const val CANNY_HIGH = 135
        const val JPEG_QUALITY = 94

        /**
         * Aturan varian B (watermark samar).
         *
         * Pada 106 pita atas/bawah chapter 19, pita kosong persis 0.0000
         * konten samar, sedangkan pita varian B di 0.0147-0.0183. Ambang
         * 0.008-0.05 duduk di celah lebar: 0.0022 di bawah, 0.0147 di atas.
         */
        const val FAINT_BANNER_MIN = 0.008
        const val FAINT_BANNER_MAX = 0.05
        const val BANNER_WHITE_MIN = 0.95

        val TEMPLATE_RES = intArrayOf(
            R.raw.banner_690, R.raw.banner_800, R.raw.banner_1280, R.raw.banner_2000
        )
    }

    private var templates: List<Template>? = null
    private var openCvReady: Boolean? = null

    /**
     * Memotong banner kalau terdeteksi. Tidak pernah melempar exception dan
     * tidak pernah menghilangkan halaman: kalau decode, OpenCV, atau penilaian
     * gagal, byte asli dikembalikan apa adanya.
     */
    fun crop(raw: ByteArray): ByteArray {
        if (!ensureOpenCv()) return raw
        return try {
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
            try {
                val side = detectCut(bmp)
                if (side == null) raw else cutBytes(bmp, side)
            } finally {
                bmp.recycle()
            }
        } catch (e: Throwable) {
            raw
        }
    }

    private fun ensureOpenCv(): Boolean {
        openCvReady?.let { return it }
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        openCvReady = ok
        return ok
    }

    private enum class Side { TOP, BOTTOM }

    private fun detectCut(bmp: Bitmap): Side? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        if (h <= 250) {
            // Halaman pendek: bisa banner utuh satu halaman, tapi tidak
            // pernah dipotong supaya tidak ikut terpotong separuh.
            return null
        }
        if (h <= BANNER_HEIGHT) return null

        val src = bmpToMat(bmp)

        // Varian B lebih dulu: murah, dan mustahil dikenali template.
        val topStats = stripStats(src, 0)
        val bottomStats = stripStats(src, h - BANNER_HEIGHT)
        val topFaint = topStats.isFaintBanner()
        val bottomFaint = bottomStats.isFaintBanner()
        if (topFaint && bottomFaint) {
            src.release()
            return if (topStats.white >= bottomStats.white) Side.TOP else Side.BOTTOM
        } else if (topFaint) {
            src.release()
            return Side.TOP
        } else if (bottomFaint) {
            src.release()
            return Side.BOTTOM
        }

        // Varian A: template matching, persis seperti web.
        val top = checkRegion(src, 0)
        val bottom = checkRegion(src, h - BANNER_HEIGHT)
        val side = when {
            top.isBanner && bottom.isBanner ->
                if (top.score >= bottom.score) Side.TOP else Side.BOTTOM
            top.isBanner -> Side.TOP
            bottom.isBanner -> Side.BOTTOM
            else -> null
        }
        src.release()
        return side
    }

    private fun bmpToMat(bmp: Bitmap): Mat {
        val m = Mat()
        Utils.bitmapToMat(bmp, m)
        return m
    }

    // ------------------------------------------------------- varian B (samar)

    private data class StripStats(val white: Double, val faint: Double) {
        fun isFaintBanner(): Boolean =
            white >= BANNER_WHITE_MIN && faint in FAINT_BANNER_MIN..FAINT_BANNER_MAX
    }

    /**
     * Rasio piksel hampir putih (abu > 245) dan rasio konten samar
     * (abu 200..250) pada pita 200px mulai baris [sy].
     */
    private fun stripStats(src: Mat, sy: Int): StripStats {
        val h = min(BANNER_HEIGHT, src.rows() - sy)
        if (h <= 0) return StripStats(0.0, 0.0)
        val band = src.submat(sy, sy + h, 0, src.cols())
        try {
            val total = band.rows() * band.cols()
            if (total <= 0) return StripStats(0.0, 0.0)

            val whiteMask = Mat()
            Imgproc.threshold(band, whiteMask, 245.0, 255.0, Imgproc.THRESH_BINARY)
            val white = Imgproc.countNonZero(whiteMask) / total.toDouble()
            whiteMask.release()

            val faintMask = Mat()
            Imgproc.threshold(band, faintMask, 200.0, 255.0, Imgproc.THRESH_BINARY)
            val faintHigh = Imgproc.countNonZero(faintMask)
            faintMask.release()
            val faintLowMask = Mat()
            Imgproc.threshold(band, faintLowMask, 250.0, 255.0, Imgproc.THRESH_BINARY)
            val faint = (faintHigh - Imgproc.countNonZero(faintLowMask)) / total.toDouble()
            faintLowMask.release()

            return StripStats(white, faint)
        } finally {
            band.release()
        }
    }

    // ------------------------------------------------- varian A (template match)

    private class RegionScore(val isBanner: Boolean, val score: Double)

    private fun checkRegion(src: Mat, sy: Int): RegionScore {
        if (src.rows() <= BANNER_HEIGHT) return RegionScore(false, 0.0)
        val regionGray = grayOf(src, sy)

        val blurred = Mat()
        Imgproc.GaussianBlur(regionGray, blurred, Size(3.0, 3.0), 0.0, 0.0, Imgproc.BORDER_DEFAULT)
        val edge = Mat()
        Imgproc.Canny(blurred, edge, CANNY_LOW.toDouble(), CANNY_HIGH.toDouble())
        val dark = darkMask(regionGray)
        val edgePixels = Imgproc.countNonZero(edge)
        val darkPixels = Imgproc.countNonZero(dark)

        val best = scoreAgainstTemplates(regionGray, edge, dark, src.cols(), edgePixels, darkPixels)

        blurred.release()
        edge.release()
        dark.release()
        regionGray.release()

        val shape = best.grayScore >= 0.30 || best.edgeScore >= 0.18 || best.darkScore >= 0.28
        val widthOk = best.widthDiff <= 0.50
        val strongText = best.darkScore >= 0.42 &&
            (best.grayScore >= 0.22 || best.edgeScore >= 0.12 || best.maskScore >= 0.88)
        val veryHigh = best.maskScore >= 0.93 && best.grayScore >= 0.45
        val normal = best.score >= MATCH_THRESHOLD && shape && widthOk
        return RegionScore(normal || strongText || veryHigh, best.score)
    }

    private fun grayOf(src: Mat, sy: Int): Mat {
        val band = src.submat(sy, sy + BANNER_HEIGHT, 0, src.cols())
        val gray = Mat()
        try {
            Imgproc.cvtColor(band, gray, Imgproc.COLOR_RGBA2GRAY)
        } finally {
            band.release()
        }
        return gray
    }

    private fun darkMask(gray: Mat): Mat {
        val dark = Mat()
        Imgproc.threshold(gray, dark, DARK_THRESHOLD.toDouble(), 255.0, Imgproc.THRESH_BINARY_INV)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(dark, dark, Imgproc.MORPH_OPEN, kernel)
        Imgproc.dilate(dark, dark, kernel)
        kernel.release()
        return dark
    }

    private class Best(
        val score: Double,
        val grayScore: Double,
        val edgeScore: Double,
        val maskScore: Double,
        val darkScore: Double,
        val widthDiff: Double
    )

    private fun scoreAgainstTemplates(
        regionRaw: Mat, regionEdge: Mat, regionDark: Mat,
        regionWidth: Int, regionEdgePixels: Int, regionDarkPixels: Int
    ): Best {
        var bestScore = 0.0
        var bestGray = 0.0
        var bestEdge = 0.0
        var bestMask = 0.0
        var bestDark = 0.0
        var bestWidthDiff = Double.MAX_VALUE

        for (t in loadTemplates()) {
            val widthDiff = abs(regionWidth - t.width).toDouble() / regionWidth
            val widthConf = when {
                widthDiff <= WIDTH_MATCH_TOLERANCE -> 1.0
                widthDiff <= 0.50 -> 0.7
                else -> 0.4
            }

            val tSize = Size(t.width.toDouble(), t.raw.rows().toDouble())
            val rRaw = Mat()
            Imgproc.resize(regionRaw, rRaw, tSize, 0.0, 0.0, Imgproc.INTER_AREA)
            val rGray = Mat()
            Imgproc.equalizeHist(rRaw, rGray)
            val rEdge = Mat()
            Imgproc.resize(regionEdge, rEdge, tSize, 0.0, 0.0, Imgproc.INTER_NEAREST)
            val rDark = Mat()
            Imgproc.resize(regionDark, rDark, tSize, 0.0, 0.0, Imgproc.INTER_NEAREST)

            val maskScore = meanAbsDiff(rRaw, t.raw, t.mask)
            val grayScore = matchSameSize(rGray, t.gray)
            val diffScore = meanAbsDiff(rRaw, t.raw, null)
            val edgeScore =
                if (regionEdgePixels > 60 && t.edgePixels > 60) matchSameSize(rEdge, t.edge) else 0.0
            val darkScore = darkSimilarity(rDark, t.dark, regionDarkPixels, t.darkPixels)

            val combined = clamp01(
                maskScore * W_MASK + grayScore * W_GRAY + edgeScore * W_EDGE +
                    darkScore * W_DARK + diffScore * W_DIFF
            )
            val adjusted = combined * widthConf

            if (adjusted > bestScore) {
                bestScore = adjusted
                bestGray = grayScore
                bestEdge = edgeScore
                bestMask = maskScore
                bestDark = darkScore
                bestWidthDiff = widthDiff
            }

            rRaw.release(); rGray.release(); rEdge.release(); rDark.release()
        }
        return Best(bestScore, bestGray, bestEdge, bestMask, bestDark, bestWidthDiff)
    }

    private fun loadTemplates(): List<Template> {
        templates?.let { return it }
        val list = ArrayList<Template>(4)
        for (res in TEMPLATE_RES) {
            val bytes = runCatching {
                context.resources.openRawResource(res).use { it.readBytes() }
            }.getOrNull() ?: continue
            val buf = MatOfByte(*bytes)
            val bmp = Imgcodecs.imdecode(buf, Imgcodecs.IMREAD_COLOR)
            if (bmp.empty()) continue
            val m = bmpToMat(bmp)
            val gray = Mat()
            Imgproc.cvtColor(m, gray, Imgproc.COLOR_RGBA2GRAY)

            val eq = Mat()
            Imgproc.equalizeHist(gray, eq)
            val blurred = Mat()
            Imgproc.GaussianBlur(eq, blurred, Size(3.0, 3.0), 0.0, 0.0, Imgproc.BORDER_DEFAULT)
            val edge = Mat()
            Imgproc.Canny(blurred, edge, CANNY_LOW.toDouble(), CANNY_HIGH.toDouble())
            val dark = darkMask(gray)
            val mask = Mat()
            Imgproc.threshold(gray, mask, MASK_THRESHOLD.toDouble(), 255.0, Imgproc.THRESH_BINARY_INV)
            val k5 = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(mask, mask, k5)
            k5.release()

            list += Template(
                raw = gray, gray = eq, edge = edge, dark = dark, mask = mask,
                width = gray.cols(),
                edgePixels = Imgproc.countNonZero(edge),
                darkPixels = Imgproc.countNonZero(dark)
            )
            blurred.release()
            m.release()
        }
        templates = list
        return list
    }

    // ------------------------------------------------------------- pemotongan

    private fun cutBytes(bmp: Bitmap, side: Side): ByteArray {
        return try {
            val w = bmp.width
            val h = bmp.height
            val cut = min(BANNER_HEIGHT, max(1, h - 1))
            val cropped = if (side == Side.TOP) {
                Bitmap.createBitmap(bmp, 0, cut, w, h - cut)
            } else {
                Bitmap.createBitmap(bmp, 0, 0, w, h - cut)
            }
            val out = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            cropped.recycle()
            out.toByteArray()
        } catch (e: Throwable) {
            ByteArray(0)
        }
    }

    // ------------------------------------------------------------------ utilitas

    /** TM_CCOEFF_NORMED untuk dua citra berukuran sama. */
    private fun matchSameSize(src: Mat, templ: Mat): Double {
        val result = Mat()
        Imgproc.matchTemplate(src, templ, result, Imgproc.TM_CCOEFF_NORMED)
        val mm = CoreMinMaxLoc(result)
        result.release()
        return clamp01(mm)
    }

    private fun CoreMinMaxLoc(m: Mat): Double {
        val minVal = DoubleArray(1)
        val maxVal = DoubleArray(1)
        val minLoc = org.opencv.core.MatOfInt()
        val maxLoc = org.opencv.core.MatOfInt()
        org.opencv.core.Core.minMaxLoc(m, minVal, maxVal, minLoc, maxLoc)
        minLoc.release()
        maxLoc.release()
        return maxVal[0]
    }

    /** 1 - rata-rata selisih absolut, opsional hanya pada piksel mask. */
    private fun meanAbsDiff(a: Mat, b: Mat, mask: Mat?): Double {
        val diff = Mat()
        Imgproc.absdiff(a, b, diff)
        val mean = org.opencv.core.Core.mean(diff, mask)[0]
        diff.release()
        return clamp01(1.0 - (if (mean.isNaN()) 255.0 else mean) / 255.0)
    }

    /** Kemirupan mask tinta: F1 dan IoU digabung 0.75/0.25, seperti web. */
    private fun darkSimilarity(regionDark: Mat, templateDark: Mat, rPx: Int, tPx: Int): Double {
        if (rPx <= 0 || tPx <= 0) return 0.0
        val inter = Mat()
        val union = Mat()
        Imgproc.bitwise_and(regionDark, templateDark, inter)
        Imgproc.bitwise_or(regionDark, templateDark, union)
        val interCount = Imgproc.countNonZero(inter)
        val unionCount = Imgproc.countNonZero(union)
        inter.release(); union.release()
        if (unionCount == 0) return 0.0
        val recall = interCount.toDouble() / tPx
        val precision = interCount.toDouble() / rPx
        val iou = interCount.toDouble() / unionCount
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        return clamp01(f1 * 0.75 + iou * 0.25)
    }

    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
}
