package com.trialfetch.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.trialfetch.app.R
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
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
        const val TAG = "BannerCropper"
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


        val TEMPLATE_RES = intArrayOf(
            R.raw.banner_690, R.raw.banner_800, R.raw.banner_1280, R.raw.banner_2000
        )
    }

    private var templates: List<Template>? = null
    private var openCvReady: Boolean? = null

    /** Berapa halaman yang diperiksa dan berapa yang benar-benar dipotong. */
    @Volatile
    var checked: Int = 0
        private set

    @Volatile
    var cut: Int = 0
        private set

    @Volatile
    var failed: Int = 0
        private set

    fun resetStats() {
        checked = 0
        cut = 0
        failed = 0
    }

    /**
     * Memotong banner kalau terdeteksi. Tidak pernah melempar exception dan
     * tidak pernah menghilangkan halaman: kalau decode, OpenCV, atau penilaian
     * gagal, byte asli dikembalikan apa adanya.
     */
    fun crop(raw: ByteArray): ByteArray {
        if (!ensureOpenCv()) {
            Log.w(TAG, "OpenCV belum siap; $checked halaman dilewati tanpa dipotong")
            return raw
        }
        return try {
            val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
            try {
                val side = detectCut(bmp)
                checked++
                if (side == null) {
                    Log.i(TAG, "halaman ${bmp.width}x${bmp.height}: tidak ada banner")
                    raw
                } else {
                    cut++
                    Log.i(TAG, "halaman ${bmp.width}x${bmp.height}: banner dipotong di $side")
                    // cutBytes mengembalikan array kosong kalau gagal
                    // encode; kalau begitu pakai gambar asli.
                    val cropped = cutBytes(bmp, side)
                    if (cropped.isEmpty()) raw else cropped
                }
            } finally {
                bmp.recycle()
            }
        } catch (e: Throwable) {
            failed++
            Log.e(TAG, "gagal memeriksa satu halaman, dilewati apa adanya", e)
            raw
        }
    }

    private fun ensureOpenCv(): Boolean {
        openCvReady?.let { return it }
        val ok = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        openCvReady = ok
        Log.i(TAG, "OpenCVLoader.initLocal() = $ok")
        return ok
    }

    private enum class Side { TOP, BOTTOM }

    private fun detectCut(bmp: Bitmap): Side? {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0) return null
        if (h in 150..250) {
            // Halaman pendek: port bannerDetectFull() dari web (simetri +
            // zona). Hasilnya SELALU dipertahankan utuh seperti web
            // (skipped:true); deteksi hanya untuk statistik/log.
            val rgba = bmpToMat(bmp)
            val gray = Mat()
            var full = false
            var fullScore = 0.0
            try {
                Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                val r = detectFullPage(gray, w, h)
                full = r.first
                fullScore = r.second
            } catch (_: Exception) {
            } finally {
                gray.release()
                rgba.release()
            }
            if (full) {
                Log.i(TAG, "halaman ${w}x${h}: banner sehalaman (skor ${"%.2f".format(fullScore)}), dipertahankan")
            }
            return null
        }
        if (h <= BANNER_HEIGHT) return null

        val src = bmpToMat(bmp)

        // Varian A: template matching, persis seperti web.
        val top = checkRegion(src, 0)
        val bottom = checkRegion(src, h - BANNER_HEIGHT)
        Log.i(
            TAG,
            "skor template atas=${"%.3f".format(top.score)} bawah=${"%.3f".format(bottom.score)}"
        )
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

    // --------------------------------- deteksi banner sehalaman (port web)
    //
    // Port bannerDetectFull() dari index.html: untuk halaman pendek,
    // web TIDAK memotong (skipped:true) tapi mencatatnya. Kriteria:
    // putih 80-99%, simetri kiri-kanan >= 0.30, tiga zona terisi sesuai
    // ambang, tepi kiri-kanan kosong (<5%) dan tengah berisi (>3%).
    // Mengembalikan (isBanner, score).
    private fun detectFullPage(gray: Mat, w: Int, h: Int): Pair<Boolean, Double> {
        if (h > 250 || h < 150 || w < 300) return Pair(false, 0.0)
        val bg = Mat()
        try {
            Imgproc.threshold(gray, bg, 245.0, 255.0, Imgproc.THRESH_BINARY)
        } catch (_: Exception) {
            bg.release()
            return Pair(false, 0.0)
        }
        val whiteRatio = Core.countNonZero(bg) / (w * h).toDouble()
        bg.release()
        if (whiteRatio < 0.80 || whiteRatio > 0.99) return Pair(false, 0.0)
        val halfW = w / 2
        val left = gray.submat(0, h, 0, halfW)
        val right = gray.submat(0, h, w - halfW, w)
        val flipped = Mat()
        val diff = Mat()
        var sym = 0.0
        try {
            Core.flip(right, flipped, 1)
            Core.absdiff(left, flipped, diff)
            val mean: Double = Core.mean(diff).`val`[0]
            sym = clamp01(1.0 - (if (mean.isNaN()) 255.0 else mean) / 255.0)
        } finally {
            left.release(); right.release(); flipped.release(); diff.release()
        }
        if (sym < 0.30) return Pair(false, 0.0)
        val thirdH = h / 3
        var validZones = 0
        val zoneDefs = arrayOf(Triple(0, 240.0, 0.01 to 0.15), Triple(thirdH, 140.0, 0.02 to 0.20), Triple(2 * thirdH, 140.0, 0.01 to 0.15))
        for ((zy, thr, range) in zoneDefs) {
            val zone = gray.submat(zy, zy + thirdH, 0, w)
            val mask = Mat()
            try {
                Imgproc.threshold(zone, mask, thr, 255.0, Imgproc.THRESH_BINARY_INV)
                val ratio = Core.countNonZero(mask) / (w * thirdH).toDouble()
                if (ratio >= range.first && ratio <= range.second) validZones++
            } finally {
                mask.release(); zone.release()
            }
        }
        if (validZones < 3) return Pair(false, 0.0)
        val centerW = w / 2
        val edgeW = w / 4
        val centerX = (w - centerW) / 2
        val centerZone = gray.submat(0, h, centerX, centerX + centerW)
        val leftEdge = gray.submat(0, h, 0, edgeW)
        val rightEdge = gray.submat(0, h, w - edgeW, w)
        val cMask = Mat(); val lMask = Mat(); val rMask = Mat()
        var centerContent = 0.0
        var leftContent = 1.0
        var rightContent = 1.0
        try {
            Imgproc.threshold(centerZone, cMask, 240.0, 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.threshold(leftEdge, lMask, 240.0, 255.0, Imgproc.THRESH_BINARY_INV)
            Imgproc.threshold(rightEdge, rMask, 240.0, 255.0, Imgproc.THRESH_BINARY_INV)
            centerContent = Core.countNonZero(cMask) / (centerW * h).toDouble()
            leftContent = Core.countNonZero(lMask) / (edgeW * h).toDouble()
            rightContent = Core.countNonZero(rMask) / (edgeW * h).toDouble()
        } finally {
            cMask.release(); lMask.release(); rMask.release()
            centerZone.release(); leftEdge.release(); rightEdge.release()
        }
        if (!(leftContent < 0.05 && rightContent < 0.05) || !(centerContent > 0.03)) {
            return Pair(false, 0.0)
        }
        val score = clamp01(whiteRatio * 0.25 + sym * 0.20 + 0.50 + 0.05)
        return Pair(score >= 0.75, score)
    }
    // ------------------------------------------------- varian A (template match)

    private class RegionScore(val isBanner: Boolean, val score: Double)

    private fun checkRegion(src: Mat, sy: Int): RegionScore {
        if (src.rows() <= BANNER_HEIGHT) return RegionScore(false, 0.0)
        val regionGray = grayOf(src, sy)

        val blurred = Mat()
        Imgproc.GaussianBlur(regionGray, blurred, Size(3.0, 3.0), 0.0, 0.0, Core.BORDER_DEFAULT)
        val edge = Mat()
        Imgproc.Canny(blurred, edge, CANNY_LOW.toDouble(), CANNY_HIGH.toDouble())
        val dark = darkMask(regionGray)
        val edgePixels = Core.countNonZero(edge)
        val darkPixels = Core.countNonZero(dark)

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
            val m = Imgcodecs.imdecode(buf, Imgcodecs.IMREAD_COLOR)
            buf.release()
            if (m.empty()) {
                m.release()
                continue
            }
            // imdecode dengan IMREAD_COLOR menghasilkan Mat 3 channel BGR,
            // bukan RGBA. Memakai COLOR_RGBA2GRAY di sini melempar exception.
            val gray = Mat()
            Imgproc.cvtColor(m, gray, Imgproc.COLOR_BGR2GRAY)

            val eq = Mat()
            Imgproc.equalizeHist(gray, eq)
            val blurred = Mat()
            Imgproc.GaussianBlur(eq, blurred, Size(3.0, 3.0), 0.0, 0.0, Core.BORDER_DEFAULT)
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
                edgePixels = Core.countNonZero(edge),
                darkPixels = Core.countNonZero(dark)
            )
            blurred.release()
            m.release()
        }
        Log.i(TAG, "template banner termuat: ${list.size} dari ${TEMPLATE_RES.size}")
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
        val mm = maxCoeff(result)
        result.release()
        return clamp01(mm)
    }

    private fun maxCoeff(m: Mat): Double {
        val r = Core.minMaxLoc(m)
        return r.maxVal
    }

    /** 1 - rata-rata selisih absolut, opsional hanya pada piksel mask. */
    private fun meanAbsDiff(a: Mat, b: Mat, mask: Mat?): Double {
        val diff = Mat()
        Core.absdiff(a, b, diff)
        // Core.mean mengembalikan Scalar; field-nya harus diakses eksplisit.
        val mean: Double = if (mask == null) {
            Core.mean(diff).`val`[0]
        } else {
            Core.mean(diff, mask).`val`[0]
        }
        diff.release()
        return clamp01(1.0 - (if (mean.isNaN()) 255.0 else mean) / 255.0)
    }

    /** Kemirupan mask tinta: F1 dan IoU digabung 0.75/0.25, seperti web. */
    private fun darkSimilarity(regionDark: Mat, templateDark: Mat, rPx: Int, tPx: Int): Double {
        if (rPx <= 0 || tPx <= 0) return 0.0
        val inter = Mat()
        val union = Mat()
        Core.bitwise_and(regionDark, templateDark, inter)
        Core.bitwise_or(regionDark, templateDark, union)
        val interCount = Core.countNonZero(inter)
        val unionCount = Core.countNonZero(union)
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
