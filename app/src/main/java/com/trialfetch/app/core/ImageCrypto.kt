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
    UNKNOWN("application/octet-stream", "bin");

    companion object {
        fun sniff(bytes: ByteArray): ImageFormat {
            if (bytes.size < 12) return UNKNOWN
            return when {
                bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> JPEG
                bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() ->
                    PNG
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
                    bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() ->
                    WEBP
                bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() ->
                    GIF
                bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() ->
                    BMP
                else -> UNKNOWN
            }
        }
    }
}
