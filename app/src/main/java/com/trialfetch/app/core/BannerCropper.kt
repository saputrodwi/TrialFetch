package com.trialfetch.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.trialfetch.app.R
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pemotong banner/watermark Baozimh.
 *
 * Ini **port** dari mesin yang dipakai Trial Fetch versi web
 * (index.html, fungsi maybeCropBanner / bannerCheckRegion /
 * bannerScoreRegion). Versi web memakai OpenCV template matching terhadap
 * 4 template banner. Di sini algoritmanya dibawa ulang ke Kotlin murni
 * supaya tidak perlu OpenCV Android SDK yang berukuran besar, tapi
 * ambang, bobot, dan urutan gerbang keptalnya sama persis.
 *
 * Prinsipnya: pita banner Baozimh selalu setinggi 200px dan bisa muncul
 * di atas atau di bawah. Pita hanya ada di sebagian halaman, jadi tidak
 * bisa dipotong buta — wilayah kandidat dibandingkan dengan 4 template
 * memakai empat sinyal sekaligus (mask, histogram abu, tepi, tinta),
 * lalu digabung dengan bobot tetap.
 *
 * Kata "template" di sini tidak berarti pixel harus sama persis. Isi
 * teks banner berubah tiap unggahan, jadi yang dibandingkan adalah
 * struktur citra, bukan warnanya.
 */
class BannerCropper(private val context: Context) {

    /** Hasil pemeriksaan satu wilayah kandidat. */
    private data class RegionScore(
        val isBanner: Boolean,
        val isFullPage: Boolean,
        val score: Double
    )

    /** Fitur satu template banner, dihitung sekali lalu dipakai ulang. */
    private class Template(
        val id: String,
        val width: Int,
        val raw: Gray,
        val gray: Gray,
        val edge: Gray,
        val dark: Gray,
        val mask: Gray,
        val edgePixels: Int,
        val darkPixels: Int
    )

    /** Citra abu-abu sederhana: nilai 0..255 per piksel. */
    private class Gray(val w: Int, val h: Int, val px: IntArray) {
        // Bernama size, bukan count: "count" bertabrakan dengan
        // Iterable.count() dan membuat .indices ambigu.
        val size: Int get() = w * h
    }

    private companion object {
        const val BANNER_HEIGHT = 200
        const val WIDTH_MATCH_TOLERANCE = 0.25
        const val MATCH_THRESHOLD = 0.58

        /** Bobot gabungan, sama seperti bannerScoreRegion di web. */
        const val W_MASK = 0.25
        const val W_GRAY = 0.30
        const val W_EDGE = 0.18
        const val W_DARK = 0.25
        const val W_DIFF = 0.02

        /**
         * Rasio piksel putih untuk pita 200px yang dianggap banner kosong.
         *
         * Template matching saja tidak cukup: pita banner yang isinya putih
         * kosong total tidak punya apa pun untuk dicocokkan dengan template,
         * jadi skornya rendah dan banner-nya lolos. Aturan rasio putih ini
         * sudah dipakai web untuk halaman pendek (hanya saja di situ tidak
         * berlaku untuk halaman tinggi), di sini diperluas ke pita mana pun.
         *
         * Diukur dari 92 pita atas/bawah chapter "我有无限金色词条" ch19:
         * 20 pita >= 0.90, hanya 1 pita di 0.80-0.90, dan 71 pita < 0.80
         * dengan nilai tertinggi 0.794. Jadi 0.90 memisahkan dengan celah
         * yang jelas, tanpa memotong satu halaman pun yang tidak bener.
         */
        const val WHITE_STRIP_THRESHOLD = 0.90

        /**
         * Batas atas rasio piksel gelap pada pita yang dianggap kosong.
         *
         * Diukur dari 92 pita chapter "我有无限金色词条" ch19: 17 pita yang
         * kosong punya tinta <= 0.009, sedangkan pita yang isinya belum
         * pasti punya 0.019 dan 0.040. Pita di antara keduanya sengaja
         * TIDAK dipotong lewat gerbang ini, dan diserahkan ke template
         * scoring agar tidak ada isi komik yang ikut terpotong.
         */
        const val EMPTY_STRIP_INK = 0.012

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

    /**
     * Memotong banner kalau terdeteksi.
     *
     * Tidak pernah melempar exception dan tidak pernah menghilangkan
     * halaman: kalau decode atau penilaian gagal, byte asli dikembalikan
     * apa adanya. Ini mengikuti perilaku web.
     */
    fun crop(raw: ByteArray): ByteArray {
        return try {
            val cut = detectCut(raw)
            if (cut == null) raw else cutBytes(raw, cut)
        } catch (e: Throwable) {
            raw
        }
    }

    private enum class Side { TOP, BOTTOM }

    /** menentukan pita mana yang perlu dipotong, atau null kalau tidak ada. */
    private fun detectCut(raw: ByteArray): Side? {
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return null
        return try {
            val w = bmp.width
            val h = bmp.height
            if (w <= 0 || h <= 0) return null

            if (h <= 250) {
                // Halaman kecil: bisa jadi banner utuh satu halaman. Baik
                // yang terdeteksi maupun tidak, halamannya dibiarkan utuh
                // supaya tidak ikut terpotong separuh.
                detectFullPage(bmp)
                return null
            }
            if (h <= BANNER_HEIGHT) return null

            // Gerbang murah lebih dulu: pita yang nyaris kosong putih tidak
            // mungkin berisi panel komik, dan tidak bisa dikenali template
            // matching karena tidak ada yang bisa dicocokkan.
            val topStrip = stripStats(bmp, 0)
            val bottomStrip = stripStats(bmp, h - BANNER_HEIGHT)
            val topEmpty = topStrip.isEmptyStrip()
            val bottomEmpty = bottomStrip.isEmptyStrip()
            if (topEmpty && bottomEmpty) {
                // Keduanya kosong: potong yang lebih putih.
                return if (topStrip.white >= bottomStrip.white) Side.TOP else Side.BOTTOM
            } else if (topEmpty) {
                return Side.TOP
            } else if (bottomEmpty) {
                return Side.BOTTOM
            }

            val top = checkRegion(bmp, Side.TOP)
            val bottom = checkRegion(bmp, Side.BOTTOM)

            return when {
                top.isFullPage && top.isBanner -> null
                top.isBanner && bottom.isBanner ->
                    if (top.score >= bottom.score) Side.TOP else Side.BOTTOM
                top.isBanner -> Side.TOP
                bottom.isBanner -> Side.BOTTOM
                else -> null
            }
        } finally {
            bmp.recycle()
        }
    }

    /** Rasio putih dan rasio tinta gelap pada pita 200px mulai [sy]. */
    private data class StripStats(val white: Double, val ink: Double) {
        /** Pita dianggap banner kosong: nyaris putih dan tanpa tinta. */
        fun isEmptyStrip(): Boolean =
            white >= WHITE_STRIP_THRESHOLD && ink <= EMPTY_STRIP_INK
    }

    /**
     * Menghitung rasio piksel putih dan gelap pada pita setinggi 200px.
     *
     * Piksel dibaca dengan sampling per baris dan per kolom supaya murah:
     * untuk gerbang yang hanya butuh perkiraan, membaca seluruh 1200x200
     * piksel per halaman itu berlebihan. Dokumentasi Android juga
     * menyarankan tidak memproses bitmap penuh saat yang dibutuhkan kecil.
     */
    private fun stripStats(bmp: Bitmap, sy: Int): StripStats {
        val w = bmp.width
        val h = min(BANNER_HEIGHT, bmp.height - sy)
        if (h <= 0) return StripStats(0.0, 0.0)

        val step = 4
        val row = IntArray(w)
        var white = 0
        var ink = 0
        var total = 0
        var y = 0
        while (y < h) {
            bmp.getPixels(row, 0, w, 0, sy + y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val gray = (r * 77 + g * 150 + b * 29) shr 8
                if (gray > 245) white++
                if (gray <= 140) ink++
                total++
                x += step
            }
            y += step
        }
        if (total == 0) return StripStats(0.0, 0.0)
        return StripStats(white.toDouble() / total, ink.toDouble() / total)
    }

    /** Halaman berukuran kecil yang seluruh isinya banner. */
    private fun detectFullPage(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (h > 250 || h < 150 || w < 300) return false
        val g = toGray(bmp, 0, 0, w, h) ?: return false

        var white = 0
        for (v in g.px) if (v > 245) white++
        val whiteShare = white.toDouble() / g.size
        if (whiteShare < 0.80 || whiteShare > 0.99) return false

        // Simetri kiri-kanan: logo dan teks banner berada di tengah.
        val halfW = w / 2
        var diffSum = 0L
        for (y in 0 until h) {
            for (x in 0 until halfW) {
                val l = g.px[y * w + x]
                val r = g.px[y * w + (w - 1 - x)]
                diffSum += abs(l - r)
            }
        }
        val sym = clamp01(1.0 - (diffSum.toDouble() / (halfW * h) / 255.0))
        if (sym < 0.30) return false

        // Tiga zone horizontal harus sama-sama jarang berisi tinta.
        val thirdH = h / 3
        var validZones = 0
        val specs = listOf(Triple(0, 240, 0.01..0.15), Triple(thirdH, 140, 0.02..0.20), Triple(2 * thirdH, 140, 0.01..0.15))
        for ((y0, thr, range) in specs) {
            if (thirdH <= 0 || y0 + thirdH > h) continue
            var ink = 0
            for (y in y0 until y0 + thirdH) {
                for (x in 0 until w) {
                    if (g.px[y * w + x] <= thr) ink++
                }
            }
            val ratio = ink.toDouble() / (w.toDouble() * thirdH)
            if (ratio >= range.start && ratio <= range.endInclusive) validZones++
        }
        return validZones >= 2
    }

    /** Menilai wilayah kandidat 200px di atas atau di bawah. */
    private fun checkRegion(bmp: Bitmap, side: Side): RegionScore {
        val w = bmp.width
        val h = bmp.height
        if (h <= BANNER_HEIGHT) return RegionScore(false, false, 0.0)

        val sy = if (side == Side.TOP) 0 else h - BANNER_HEIGHT
        val regionGray = toGray(bmp, 0, sy, w, BANNER_HEIGHT) ?: return RegionScore(false, false, 0.0)

        val blurred = gaussianBlur3(regionGray)
        val edge = canny(blurred, CANNY_LOW, CANNY_HIGH)
        val dark = darkMask(regionGray)
        val edgePixels = countNonZero(edge)
        val darkPixels = countNonZero(dark)

        val best = scoreAgainstTemplates(regionGray, edge, dark, w, edgePixels, darkPixels)

        // Gerbang yang sama seperti bannerCheckRegion di web.
        val shape = best.grayScore >= 0.30 || best.edgeScore >= 0.18 || best.darkScore >= 0.28
        val widthOk = best.widthDiff <= 0.50
        val strongText = best.darkScore >= 0.42 &&
            (best.grayScore >= 0.22 || best.edgeScore >= 0.12 || best.maskScore >= 0.88)
        val veryHigh = best.maskScore >= 0.93 && best.grayScore >= 0.45
        val normal = best.score >= MATCH_THRESHOLD && shape && widthOk
        val isBanner = normal || strongText || veryHigh

        return RegionScore(isBanner, false, best.score)
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
        regionRaw: Gray,
        regionEdge: Gray,
        regionDark: Gray,
        regionWidth: Int,
        regionEdgePixels: Int,
        regionDarkPixels: Int
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

            val rRaw = resizeArea(regionRaw, t.width, t.raw.h)
            val rGray = equalizeHist(rRaw)
            val rEdge = resizeNearest(regionEdge, t.width, t.edge.h)
            val rDark = resizeNearest(regionDark, t.width, t.dark.h)

            val maskScore = meanAbsDiff(rRaw, t.raw, t.mask)
            val grayScore = tmCoeffNormed(rGray, t.gray)
            val diffScore = meanAbsDiff(rRaw, t.raw, null)
            val edgeScore =
                if (regionEdgePixels > 60 && t.edgePixels > 60) tmCoeffNormed(rEdge, t.edge) else 0.0
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
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
            val g = toGray(bmp, 0, 0, bmp.width, bmp.height)
            bmp.recycle()
            if (g == null) continue

            val eq = equalizeHist(g)
            val edge = canny(gaussianBlur3(eq), CANNY_LOW, CANNY_HIGH)
            val dark = darkMask(g)
            val mask = maskOf(g)
            list += Template(
                id = "banner",
                width = g.w,
                raw = g,
                gray = eq,
                edge = edge,
                dark = dark,
                mask = mask,
                edgePixels = countNonZero(edge),
                darkPixels = countNonZero(dark)
            )
        }
        templates = list
        return list
    }

    // ------------------------------------------------------------- pemotongan

    private fun cutBytes(raw: ByteArray, side: Side): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
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
            // Web selalu menulis ulang ke JPEG 94; sama supaya hasil file
            // di app dan di web tidak berbeda.
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            cropped.recycle()
            out.toByteArray()
        } catch (e: Throwable) {
            raw
        } finally {
            bmp.recycle()
        }
    }

    // ------------------------------------------------------------ utilitas citra

    private fun toGray(bmp: Bitmap, sx: Int, sy: Int, w: Int, h: Int): Gray? {
        if (sx < 0 || sy < 0 || sx + w > bmp.width || sy + h > bmp.height) return null
        val px = IntArray(w * h)
        val row = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, sx, sy + y, w, 1)
            val base = y * w
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                px[base + x] = (r * 77 + g * 150 + b * 29) shr 8
            }
        }
        return Gray(w, h, px)
    }

    /** Kernel [1 2 1] satu dimensi, sama seperti GaussianBlur 3x3 OpenCV. */
    private fun gaussianBlur3(src: Gray): Gray {
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        for (y in 0 until src.h) {
            val base = y * src.w
            for (x in 0 until src.w) {
                val l = if (x > 0) src.px[base + x - 1] else src.px[base + x]
                val c = src.px[base + x]
                val r = if (x < src.w - 1) src.px[base + x + 1] else src.px[base + x]
                tmp[base + x] = (l + 2 * c + r) / 4
            }
        }
        for (x in 0 until src.w) {
            for (y in 0 until src.h) {
                val u = if (y > 0) tmp[(y - 1) * src.w + x] else tmp[y * src.w + x]
                val c = tmp[y * src.w + x]
                val d = if (y < src.h - 1) tmp[(y + 1) * src.w + x] else tmp[y * src.w + x]
                out[y * src.w + x] = (u + 2 * c + d) / 4
            }
        }
        return Gray(src.w, src.h, out)
    }

    /** Canny sederhana: Sobel + non-maximum suppression + hysteresis. */
    private fun canny(src: Gray, low: Int, high: Int): Gray {
        val w = src.w
        val h = src.h
        val gx = IntArray(src.size)
        val gy = IntArray(src.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val tl = src.px[i - w - 1]; val tc = src.px[i - w]; val tr = src.px[i - w + 1]
                val ml = src.px[i - 1]; val mr = src.px[i + 1]
                val bl = src.px[i + w - 1]; val bc = src.px[i + w]; val br = src.px[i + w + 1]
                gx[i] = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                gy[i] = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
            }
        }
        val mag = IntArray(src.size)
        var maxMag = 1
        for (i in 0 until src.size) {
            val m = abs(gx[i]) + abs(gy[i])
            mag[i] = m
            if (m > maxMag) maxMag = m
        }
        val strong = BooleanArray(src.size)
        val weak = BooleanArray(src.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val gxv = gx[i]
                val gyv = gy[i]
                val dir = when {
                    gxv > 0 && gyv > 0 -> if (gxv > gyv) 1 else 2
                    gxv < 0 && gyv < 0 -> if (gxv > gyv) 3 else 2
                    gxv >= 0 -> 0
                    else -> 0
                }
                val m = mag[i]
                val n1 = neighbor(dir, mag, w, h, x, y, 1)
                val n2 = neighbor(dir, mag, w, h, x, y, 2)
                if (m >= n1 && m >= n2) {
                    val tHigh = high.toDouble() / 255.0 * maxMag
                    val tLow = low.toDouble() / 255.0 * maxMag
                    if (m >= tHigh) strong[i] = true else if (m >= tLow) weak[i] = true
                }
            }
        }
        val stack = ArrayDeque<Int>()
        for (i in 0 until strong.size) if (strong[i]) stack.addLast(i)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val x = i % w
            val y = i / w
            for (dy in -1..1) {
                for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (weak[j]) {
                        weak[j] = false
                        strong[j] = true
                        stack.addLast(j)
                    }
                }
            }
        }
        val out = IntArray(src.size)
        for (i in 0 until src.size) out[i] = if (strong[i]) 255 else 0
        return Gray(w, h, out)
    }

    private fun neighbor(dir: Int, mag: IntArray, w: Int, h: Int, x: Int, y: Int, step: Int): Int {
        val nx = when (dir) {
            0 -> x + step
            1 -> x + step
            2 -> x
            3 -> x - step
            else -> x - step
        }
        val ny = when (dir) {
            0 -> y
            1 -> y + step
            2 -> y - step
            3 -> y
            else -> y
        }
        if (nx < 0 || ny < 0 || nx >= w || ny >= h) return 0
        return mag[ny * w + nx]
    }

    /** threshold 135 inv, morphological open 3x3, lalu dilate 3x3. */
    private fun darkMask(g: Gray): Gray {
        var cur = thresholdInv(g, DARK_THRESHOLD)
        cur = erode3(cur)
        cur = dilate(cur, 3)
        cur = dilate(cur, 3)
        return cur
    }

    /** threshold 246 inv lalu dilate 5x5. */
    private fun maskOf(g: Gray): Gray {
        val t = thresholdInv(g, MASK_THRESHOLD)
        return dilate(t, 5)
    }

    private fun thresholdInv(g: Gray, t: Int): Gray {
        val out = IntArray(g.size)
        for (i in 0 until g.size) out[i] = if (g.px[i] <= t) 255 else 0
        return Gray(g.w, g.h, out)
    }

    private fun dilate(src: Gray, k: Int): Gray {
        val r = k / 2
        val out = IntArray(src.size)
        for (y in 0 until src.h) {
            for (x in 0 until src.w) {
                var v = 0
                for (dy in -r..r) {
                    val ny = y + dy
                    if (ny < 0 || ny >= src.h) continue
                    for (dx in -r..r) {
                        val nx = x + dx
                        if (nx < 0 || nx >= src.w) continue
                        if (src.px[ny * src.w + nx] != 0) {
                            v = 255
                        }
                    }
                }
                out[y * src.w + x] = v
            }
        }
        return Gray(src.w, src.h, out)
    }

    private fun erode3(src: Gray): Gray {
        val out = IntArray(src.size)
        for (y in 0 until src.h) {
            for (x in 0 until src.w) {
                var v = 255
                for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= src.h) { v = 0; continue }
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= src.w || src.px[ny * src.w + nx] == 0) {
                            v = 0
                        }
                    }
                }
                out[y * src.w + x] = v
            }
        }
        return Gray(src.w, src.h, out)
    }

    private fun equalizeHist(src: Gray): Gray {
        val hist = IntArray(256)
        for (v in src.px) hist[v]++
        val out = IntArray(src.size)
        val total = src.size
        var acc = 0
        val lut = IntArray(256)
        for (i in 0..255) {
            acc += hist[i]
            lut[i] = ((acc - hist[i] / 2).toDouble() / total * 255.0).roundToInt().coerceIn(0, 255)
        }
        for (i in 0 until src.size) out[i] = lut[src.px[i]]
        return Gray(src.w, src.h, out)
    }

    /** Resize rata-rata area, setara cv.INTER_AREA. */
    private fun resizeArea(src: Gray, dw: Int, dh: Int): Gray {
        if (src.w == dw && src.h == dh) return src
        val out = IntArray(dw * dh)
        val xr = src.w.toDouble() / dw
        val yr = src.h.toDouble() / dh
        for (y in 0 until dh) {
            val y0 = (y * yr).toInt()
            val y1 = max(y0 + 1, ((y + 1) * yr).toInt())
            for (x in 0 until dw) {
                val x0 = (x * xr).toInt()
                val x1 = max(x0 + 1, ((x + 1) * xr).toInt())
                var sum = 0L
                var n = 0
                for (yy in y0 until min(y1, src.h)) {
                    val base = yy * src.w
                    for (xx in x0 until min(x1, src.w)) {
                        sum += src.px[base + xx]
                        n++
                    }
                }
                out[y * dw + x] = if (n == 0) 0 else (sum / n).toInt()
            }
        }
        return Gray(dw, dh, out)
    }

    private fun resizeNearest(src: Gray, dw: Int, dh: Int): Gray {
        if (src.w == dw && src.h == dh) return src
        val out = IntArray(dw * dh)
        val xr = if (dw > 0) src.w.toDouble() / dw else 1.0
        val yr = if (dh > 0) src.h.toDouble() / dh else 1.0
        for (y in 0 until dh) {
            val sy = min(src.h - 1, (y * yr).toInt())
            for (x in 0 until dw) {
                val sx = min(src.w - 1, (x * xr).toInt())
                out[y * dw + x] = src.px[sy * src.w + sx]
            }
        }
        return Gray(dw, dh, out)
    }

    private fun countNonZero(g: Gray): Int {
        var n = 0
        for (v in g.px) if (v != 0) n++
        return n
    }

    /** 1 - rata-rata selisih absolut, opsional hanya dihitung pada mask. */
    private fun meanAbsDiff(a: Gray, b: Gray, mask: Gray?): Double {
        var sum = 0L
        var n = 0
        for (i in 0 until a.size) {
            if (mask != null && mask.px[i] == 0) continue
            sum += abs(a.px[i] - b.px[i])
            n++
        }
        val mean = if (n == 0) 255.0 else sum.toDouble() / n
        return clamp01(1.0 - (mean / 255.0))
    }

    /**
     * Setara cv.TM_CCOEFF_NORMED untuk dua citra berukuran sama: pada
     * ukuran sama hanya ada satu posisi, jadi hasilnya simply koefisien
     * korelasi kedua citra.
     */
    private fun tmCoeffNormed(a: Gray, b: Gray): Double {
        val n = a.size
        if (n == 0 || b.size != n) return 0.0
        var sumA = 0L
        var sumB = 0L
        for (i in 0 until n) {
            sumA += a.px[i]
            sumB += b.px[i]
        }
        val meanA = sumA.toDouble() / n
        val meanB = sumB.toDouble() / n
        var num = 0.0
        var da = 0.0
        var db = 0.0
        for (i in 0 until n) {
            val va = a.px[i] - meanA
            val vb = b.px[i] - meanB
            num += va * vb
            da += va * va
            db += vb * vb
        }
        val den = sqrt(da) * sqrt(db)
        if (den <= 1e-9) return 0.0
        return clamp01(abs(num) / den)
    }

    /** Kemirupan mask tinta lewat F1 dari irisan dan gabungan. */
    private fun darkSimilarity(regionDark: Gray, tmplDark: Gray, rPx: Int, tPx: Int): Double {
        if (rPx <= 0 || tPx <= 0) return 0.0
        var inter = 0
        var union = 0
        for (i in 0 until regionDark.size) {
            val a = regionDark.px[i] != 0
            val b = tmplDark.px[i] != 0
            if (a && b) inter++
            if (a || b) union++
        }
        if (union == 0) return 0.0
        val recall = inter.toDouble() / tPx
        val precision = inter.toDouble() / rPx
        val iou = inter.toDouble() / union
        val f1 = if (precision + recall > 0) 2 * precision * recall / (precision + recall) else 0.0
        // Web menggabungkan F1 dan IoU dengan bobot 0.75/0.25; ditiru apa adanya.
        return clamp01(f1 * 0.75 + iou * 0.25)
    }

    private fun clamp01(v: Double): Double = if (v < 0.0) 0.0 else if (v > 1.0) 1.0 else v
}
