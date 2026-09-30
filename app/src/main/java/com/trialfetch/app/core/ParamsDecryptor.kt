package com.trialfetch.app.core

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sumber yang halamannya menyembunyikan daftar gambar di dalam blob
 * terenkripsi: variabel JS `params = '...'` berisi base64 dari
 * AES-128-CBC. Setelah dekripsi didapat JSON { source_id, images[] }.
 *
 * Kunci & aturan IV berasal dari parameter situs yang terverifikasi
 * (dipakai identik oleh manwang.net dan rumanhua.org — keduanya situs
 * kembar dengan backend sama).
 */
object ParamsDecryptor {

    private const val KEY = "9S8\$vJnU2ANeSRoF"
    private const val ALGORITHM = "AES/CBC/PKCS5Padding"
    private const val MIN_BLOB_LENGTH = 100

    /** Ditemukan di html chapter: params = '...' */
    private val paramsRe = Regex("""params\s*=\s*['"]([^'"]{$MIN_BLOB_LENGTH,})""")

    data class Decoded(
        val images: List<String>,
        /** true bila tiap file gambar perlu AES terenkripsi (source_id "12"). */
        val imageEncrypted: Boolean,
        val comicId: String?,
        val chapterId: String?
    )

    /**
     * Ambil blob `params` dari HTML lalu dekripsi.
     * @return null kalau HTML tidak punya blob params.
     */
    fun fromHtml(html: String): Decoded? {
        val blob = paramsRe.find(html)?.groupValues?.get(1) ?: return null
        return decode(blob)
    }

    fun decode(blob: String): Decoded {
        val normalized = blob.trim()
            .replace("-", "+")
            .replace("_", "/")
        val padded = normalized + "=".repeat((4 - (normalized.length % 4)) % 4)
        val raw = try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            throw SourceException("Blob params bukan base64 yang valid", e)
        }
        if (raw.size <= 16) {
            throw SourceException("Blob params terlalu pendek (${raw.size} byte)")
        }

        val iv = raw.copyOfRange(0, 16)
        val ciphertext = raw.copyOfRange(16, raw.size)
        val key: Key = SecretKeySpec(KEY.toByteArray(Charsets.UTF_8), "AES")

        val plaintext = try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw SourceException("Gagal dekripsi params: ${e.message}", e)
        }

        val json = try {
            Json.parseToJsonElement(String(plaintext, Charsets.UTF_8)) as? JsonObject
                ?: throw SourceException("Hasil dekripsi bukan objek JSON")
        } catch (e: SourceException) {
            throw e
        } catch (e: Exception) {
            throw SourceException("JSON params rusak: ${e.message}", e)
        }

        val images = json["images"]?.jsonArray.orEmpty()
            .mapNotNull { element ->
                when (element) {
                    is JsonPrimitive -> element.content
                    is JsonObject -> element["url"]?.jsonPrimitive?.content
                        ?: element["src"]?.jsonPrimitive?.content
                    else -> null
                }
            }
            .filter { it.startsWith("http") }

        val sourceId = json["source_id"]?.jsonPrimitive?.contentOrNullSafe()

        return Decoded(
            images = images,
            imageEncrypted = sourceId == "12",
            comicId = json["comic_id"]?.jsonPrimitive?.contentOrNullSafe(),
            chapterId = json["chapter_id"]?.jsonPrimitive?.contentOrNullSafe()
        )
    }
}

private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content.takeIf { it.isNotBlank() }
