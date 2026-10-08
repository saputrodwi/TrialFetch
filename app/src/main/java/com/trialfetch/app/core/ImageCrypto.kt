package com.trialfetch.app.core

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dekripsi AES-128-CBC untuk file gambar terenkripsi.
 *
 * Dipakai Manwa: file gambarnya bukan gambar biasa, melainkan ciphertext
 * AES-128-CBC dengan key & IV yang sama-sama statis.
 */
object ImageCrypto {
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"

    /**
     * Dekripsi buffer terenkripsi.
     *
     * @param keyStr key statis situs (16 byte untuk AES-128)
     * @param iv    IV; pada sebagian situs IV = key, pada sebagian lain
     *              IV adalah 16 byte pertama dari payload lalu sisanya
     *              ciphertext. Parameter [ivFromPrefix] memilih yang mana.
     */
    fun decrypt(
        data: ByteArray,
        keyStr: String,
        iv: ByteArray = keyStr.toByteArray(Charsets.UTF_8),
        ivFromPrefix: Boolean = false
    ): ByteArray {
        val keyBytes = keyStr.toByteArray(Charsets.UTF_8)
        val (realIv, ciphertext) = if (ivFromPrefix) {
            require(data.size > 16) { "payload terlalu pendek untuk IV di-awal" }
            data.copyOfRange(0, 16) to data.copyOfRange(16, data.size)
        } else {
            iv to data
        }

        val key: Key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(realIv))
        return cipher.doFinal(ciphertext)
    }
}

/** Format hasil dekripsi — dipakai untuk membedakan gambar vs junk. */
enum class ImageFormat(val mime: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    WEBP("image/webp", "webp"),
    GIF("image/gif", "gif"),
    BMP("image/bmp", "bmp"),
    AVIF("image/avif", "avif"),
    HEIC("image/heic", "heic"),
    JXL("image/jxl", "jxl"),
    UNKNOWN("application/octet-stream", "bin");

    companion object {
        fun sniff(bytes: ByteArray): ImageFormat {
            if (bytes.size < 12) return UNKNOWN
            // JXL: 0xFF 0x0A, atau box "ftyp" dengan brand "jxl ".
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0x0A.toByte()) return JXL
            return when {
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> JPEG
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() ->
                    PNG
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                    bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                    ascii(bytes, 8, 12) == "WEBP" ->
                    WEBP
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() ->
                    GIF
                bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() ->
                    BMP
                isFtyp(bytes, "avif") || isFtyp(bytes, "avis") -> AVIF
                isFtyp(bytes, "heic") || isFtyp(bytes, "heix") ||
                    isFtyp(bytes, "mif1") -> HEIC
                else -> {
                    // Cara praktis: Box "ftyp" + brand berawalan jxl/jpegxl.
                    val brand = ascii(bytes, 8, 12)
                    when {
                        brand.startsWith("jxl") || brand.startsWith("avif") -> JXL
                        else -> UNKNOWN
                    }
                }
            }
        }

        /**
         * sniff() lalu fallback ke BitmapFactory (inJustDecodeBounds) untuk
         * format yang tidak dikenal tanda-tangan awalnya, mis. AVIF/HEIC
         * dengan brand tak biasa. Mengembalikan UNKNOWN bila juga gagal —
         * pemanggil menandainya gagal daripada menyimpan file kosong.
         */
        fun guessWithBitmap(bytes: ByteArray): ImageFormat {
            val byMagic = sniff(bytes)
            if (byMagic != UNKNOWN) return byMagic
            return try {
                val opts = android.graphics.BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                fromMime(opts.outMimeType) ?: UNKNOWN
            } catch (e: Exception) {
                UNKNOWN
            }
        }

        private fun fromMime(mime: String?): ImageFormat? = when (mime?.lowercase()) {
            "image/jpeg" -> JPEG
            "image/png" -> PNG
            "image/webp" -> WEBP
            "image/gif" -> GIF
            "image/bmp", "image/x-ms-bmp" -> BMP
            "image/avif" -> AVIF
            "image/heic", "image/heic-sequence", "image/heif" -> HEIC
            "image/jxl" -> JXL
            else -> null
        }

        /** true bila bytes[4..7] == "ftyp" dan brand berisi [want]. */
        private fun isFtyp(bytes: ByteArray, want: String): Boolean {
            if (bytes.size < 12) return false
            if (bytes[4] != 0x66.toByte() || bytes[5] != 0x74.toByte() ||
                bytes[6] != 0x79.toByte() || bytes[7] != 0x70.toByte()
            ) return false
            return ascii(bytes, 8, 12).lowercase().contains(want)
        }

        private fun ascii(bytes: ByteArray, from: Int, to: Int): String {
            return bytes.copyOfRange(from, to.coerceAtMost(bytes.size))
                .toString(Charsets.US_ASCII)
        }
    }
}
