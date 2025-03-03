package com.tpk.widgetpro.api

import android.content.Context
import android.content.pm.PackageManager
import android.util.Base64
import com.tpk.widgetpro.models.GoogleSearchResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.security.MessageDigest
import kotlin.coroutines.resume
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec


object ImageApiClient {
    private const val GOOGLE_API_BASE_URL = "https://www.googleapis.com/"
    private const val PREF_NAME = "ImageCache"
    private const val URL_CACHE_PREFIX = "url_"
    private const val TIMESTAMP_PREFIX = "ts_"
    private const val WEEK_IN_MILLIS = 604_800_000L

    val fernetKey = "pJKQQBG9i9MpyZP-Funnm7YK_YjtAYQklpt2GrKyz3Q="
    val encryptedApiKey1 = "gAAAAABnvhQ36Xqb1A67-ogOlvOFB001hqYY7c5M1sBZg2bUzkOGr_8O6dxEWkNh1NdS7RgsxARCzYs50y7J_74y5GNNXkHMJBtfBB2-Dt3CHBuLIx97JvOlX8h0HH4nzN_0AJdBOocw"
    val encryptedApiKey2 = "gAAAAABnvhQ31AM1FKa-g8sXN3sjifngDPxu_zHRxdlyctVt_JuGHF8nkQ4dD97iOR-trrgTE59gkYoDW-suWTCz6dxuZ3iKC3SlK_kiXTJ19OeRRxQgVBA="
    val decryptedApiKey1 = decryptApiKey(encryptedApiKey1, fernetKey)
    val decryptedApiKey2 = decryptApiKey(encryptedApiKey2, fernetKey)
    private val API_KEY = decryptedApiKey1
    private val SEARCH_ENGINE_ID = decryptedApiKey2

    private fun createService(context: Context): GoogleSearchApiService {

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()

                val packageName = context.packageName

                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signatures = packageInfo.signingInfo?.apkContentsSigners
                val signature = signatures?.get(0)?.toByteArray()
                val md = MessageDigest.getInstance("SHA1")
                val sha1Bytes = md.digest(signature)
                val sha1Hex = sha1Bytes.joinToString("") { "%02x".format(it) }

                val newRequest = originalRequest.newBuilder()
                    .header("X-Android-Package", packageName)
                    .header("X-Android-Cert", sha1Hex)
                    .build()

                chain.proceed(newRequest)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(GOOGLE_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GoogleSearchApiService::class.java)
    }

    suspend fun getImageUrl(context: Context, modelName: String): String {
        return suspendCancellableCoroutine { continuation ->
            val query = buildSearchQuery(modelName)
            val service = createService(context)

            service.searchImages(API_KEY, SEARCH_ENGINE_ID, query, "image", "png")
                .enqueue(object : retrofit2.Callback<GoogleSearchResponse> {
                    override fun onResponse(
                        call: retrofit2.Call<GoogleSearchResponse>,
                        response: retrofit2.Response<GoogleSearchResponse>
                    ) {
                        continuation.resume(response.body()?.items?.firstOrNull()?.link ?: "")
                    }

                    override fun onFailure(
                        call: retrofit2.Call<GoogleSearchResponse>,
                        t: Throwable
                    ) {
                        continuation.resume("")
                    }
                })
        }
    }

    private fun buildSearchQuery(modelName: String): String {
        return when {
            modelName == "Unknown" -> "bluetooth device icon transparent"
            modelName.contains("watch", ignoreCase = true) -> sanitizeDeviceName(modelName)
            else -> "$modelName transparent"
        }
    }

    fun getCachedUrl(context: Context, modelName: String): String? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val timestamp = prefs.getLong(TIMESTAMP_PREFIX + modelName, 0)

        return if (System.currentTimeMillis() - timestamp < WEEK_IN_MILLIS) {
            prefs.getString(URL_CACHE_PREFIX + modelName, null)
        } else {
            prefs.edit().remove(URL_CACHE_PREFIX + modelName)
                .remove(TIMESTAMP_PREFIX + modelName)
                .apply()
            null
        }
    }

    fun cacheUrl(context: Context, modelName: String, url: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(URL_CACHE_PREFIX + modelName, url)
            .putLong(TIMESTAMP_PREFIX + modelName, System.currentTimeMillis())
            .apply()
    }

    private fun sanitizeDeviceName(originalName: String?): String {
        if (originalName.isNullOrEmpty()) return "Unknown Device"

        val suffixesToRemove = listOf(
            "\\s*-?\\s*LE$",                    // " LE" or "-LE" at end
            "\\s*\\(.*\\)$",                    // Anything in parentheses at end
            "\\s*[_-]\\s*[A-Za-z0-9]{4}$",      // Model numbers like "_3D9F" or "-2020"
            "\\s*[_-]\\s*[vV]\\d+",             // Version numbers like "_v5"
            "\\s*Pro$",                         // " Pro" suffix
            "\\s*Lite$",                        // " Lite" suffix
            "\\s*[_-]?\\s*[Bb][Ll][Ee]$"        // BLE variants
        )

        var sanitized = originalName.trim()

        suffixesToRemove.forEach { pattern ->
            sanitized = sanitized.replace(Regex(pattern, RegexOption.IGNORE_CASE), "")
        }

        return sanitized
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")    // Remove special chars except spaces
            .trim()
            .replace(Regex("\\s{2,}"), " ")           // Replace multiple spaces with one
            .lowercase()
            .replaceFirstChar { it.uppercaseChar() }                    // Capitalize first letter
    }

    fun decryptApiKey(encryptedToken: String, fernetKey: String): String {
        val decodedKey = decodeBase64Url(fernetKey)
        if (decodedKey.size != 32) throw IllegalArgumentException("Invalid Fernet key")

        val signingKey = decodedKey.copyOfRange(0, 16)
        val encryptionKey = decodedKey.copyOfRange(16, 32)

        val tokenBytes = decodeBase64Url(encryptedToken)

        val version = tokenBytes[0]
        if (version != 0x80.toByte()) throw IllegalArgumentException("Invalid token version")

        val timestamp = tokenBytes.copyOfRange(1, 9)
        val iv = tokenBytes.copyOfRange(9, 25)
        val ciphertext = tokenBytes.copyOfRange(25, tokenBytes.size - 32)
        val receivedHmac = tokenBytes.copyOfRange(tokenBytes.size - 32, tokenBytes.size)

        val dataToHmac = tokenBytes.copyOfRange(0, tokenBytes.size - 32)
        verifyHmac(dataToHmac, receivedHmac, signingKey)

        return decryptAesCbc(ciphertext, encryptionKey, iv)
    }

    private fun decodeBase64Url(input: String): ByteArray {
        val base64 = input.replace('-', '+').replace('_', '/')
        val padding = (4 - base64.length % 4) % 4
        val paddedBase64 = base64 + "=".repeat(padding)
        return Base64.decode(paddedBase64, Base64.DEFAULT)
    }

    private fun verifyHmac(data: ByteArray, receivedHmac: ByteArray, hmacKey: ByteArray) {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(hmacKey, "HmacSHA256"))
        val computedHmac = mac.doFinal(data)
        if (!computedHmac.contentEquals(receivedHmac)) {
            throw SecurityException("HMAC verification failed")
        }
    }

    private fun decryptAesCbc(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        val decrypted = cipher.doFinal(ciphertext)
        return String(decrypted, Charsets.UTF_8)
    }
}
