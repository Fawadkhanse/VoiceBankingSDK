package com.voicebanking.sdk.data

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Generates short-lived OAuth2 Bearer tokens from a Google Service Account.
 * Token is cached and reused until 5 minutes before expiry.
 */
internal class ServiceAccountAuth(
    private val clientEmail: String,
    private val privateKeyPem: String
) {
    private val TAG       = "SDK_Auth"
    private val TOKEN_URL = "https://oauth2.googleapis.com/token"
    private val SCOPE     = "https://www.googleapis.com/auth/cloud-platform"

    private var cachedToken:   String? = null
    private var tokenExpiryMs: Long    = 0L

    suspend fun getBearerToken(): String = withContext(Dispatchers.IO) {
        val nowMs = System.currentTimeMillis()
        if (cachedToken != null && nowMs < tokenExpiryMs - 5 * 60 * 1000) {
            return@withContext cachedToken!!
        }
        Log.d(TAG, "Fetching new OAuth2 token…")
        val token = fetchNewToken()
        cachedToken   = token
        tokenExpiryMs = nowMs + 3_600_000L
        token
    }

    private fun fetchNewToken(): String {
        val nowSec = System.currentTimeMillis() / 1000
        val expSec = nowSec + 3600

        val header  = base64url("""{"alg":"RS256","typ":"JWT"}""")
        val payload = base64url(
            """{"iss":"$clientEmail","scope":"$SCOPE","aud":"$TOKEN_URL","iat":$nowSec,"exp":$expSec}"""
        )

        val signingInput = "$header.$payload"
        val sig = Signature.getInstance("SHA256withRSA").apply {
            initSign(loadPrivateKey())
            update(signingInput.toByteArray(Charsets.US_ASCII))
        }.sign()
        val signature = Base64.encodeToString(sig, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val jwt = "$signingInput.$signature"

        val postBody = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer&assertion=$jwt"
        val conn = (URL(TOKEN_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout    = 15_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(postBody.toByteArray()) }

        val code = conn.responseCode
        val body = if (code == 200) conn.inputStream.bufferedReader().readText()
                   else             conn.errorStream.bufferedReader().readText()
        conn.disconnect()

        if (code != 200) throw RuntimeException("OAuth2 token error $code: $body")
        return JSONObject(body).getString("access_token")
    }

    private fun loadPrivateKey(): PrivateKey {
        val cleaned = privateKeyPem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\n", "").replace("\n", "").trim()
        return KeyFactory.getInstance("RSA")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(cleaned, Base64.DEFAULT)))
    }

    private fun base64url(json: String): String =
        Base64.encodeToString(json.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
