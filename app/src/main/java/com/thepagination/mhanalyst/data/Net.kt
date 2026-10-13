package com.thepagination.mhanalyst.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ApiException(message: String, val code: Int) : Exception(message)

/** Talks to api/app.php on the MH Analyst website. Android unpacks gzip answers automatically. */
class Api(private val base: String, private val token: () -> String?) {
    fun call(route: String, query: Map<String, String> = emptyMap(), body: JSONObject? = null): JSONObject {
        val url = buildString {
            append(base).append("api/app.php?r=").append(route)
            for ((k, v) in query) append('&').append(k).append('=').append(URLEncoder.encode(v, "UTF-8"))
        }
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 90000
        conn.requestMethod = if (body != null) "POST" else "GET"
        conn.setRequestProperty("Accept", "application/json")
        token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw ApiException("The website sent an unexpected answer (HTTP $code).", code)
            }
            if (code !in 200..299 || !json.optBoolean("ok", false)) {
                throw ApiException(json.optString("error", "Something went wrong (HTTP $code)."), code)
            }
            return json
        } catch (e: IOException) {
            throw ApiException("Couldn't reach the website. Check the internet connection.", 0)
        } finally {
            conn.disconnect()
        }
    }
}

/** The phone key, encrypted with a key that never leaves Android's secure keystore. */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("mh_secure", Context.MODE_PRIVATE)
    private val alias = "mh_phone_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    var token: String?
        get() = prefs.getString("t", null)?.let { decrypt(it) }
        set(value) {
            if (value == null) prefs.edit().remove("t").apply() else prefs.edit().putString("t", encrypt(value)).apply()
        }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = try {
        val raw = Base64.decode(stored, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        String(c.doFinal(raw.copyOfRange(12, raw.size)), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}

data class Company(val key: String, val name: String)

data class Profile(
    val username: String,
    val name: String,
    val role: String,
    val canCreate: Boolean,
    val companies: List<Company>,
    val appName: String,
    val hidePrefixes: List<String>,
    val hideSuffixes: List<String>,
) {
    fun hidden(item: String): Boolean {
        val n = item.trim().lowercase()
        return hidePrefixes.any { it.isNotEmpty() && n.startsWith(it.lowercase()) } || hideSuffixes.any { it.isNotEmpty() && n.endsWith(it.lowercase()) }
    }

    companion object {
        fun from(j: JSONObject): Profile {
            val u = j.getJSONObject("user")
            val s = j.optJSONObject("settings") ?: JSONObject()
            val cos = j.getJSONArray("companies")
            val split = { v: String -> v.split(',').map { it.trim() }.filter { it.isNotEmpty() } }
            return Profile(
                username = u.optString("username"),
                name = u.optString("name"),
                role = u.optString("role"),
                canCreate = j.optBoolean("can_create"),
                companies = (0 until cos.length()).map { val c = cos.getJSONObject(it); Company(c.getString("key"), c.optString("name")) },
                appName = s.optString("app_name", "MH Analyst"),
                hidePrefixes = split(s.optString("hide_prefixes", "-x")),
                hideSuffixes = split(s.optString("hide_suffixes", "-x")),
            )
        }
    }
}
