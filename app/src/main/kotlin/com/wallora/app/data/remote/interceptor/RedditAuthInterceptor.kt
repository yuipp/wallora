package com.wallora.app.data.remote.interceptor

import android.util.Log
import com.wallora.app.di.UserKeyCache
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OkHttp interceptor that handles Reddit userless (application-only) OAuth.
 *
 * Flow:
 * 1. On each request: if no token or token is expiring soon, fetch a fresh bearer token
 *    via the installed-app OAuth grant (`grant_type=https://oauth.reddit.com/grants/installed_client`).
 * 2. Attach `Authorization: Bearer <token>` + the required distinctive User-Agent.
 * 3. On HTTP 401: invalidate cached token and retry once.
 *
 * Token fetches are done synchronously (OkHttp interceptor is blocking) using a separate
 * bare OkHttpClient ([tokenClient]) to avoid circular dependency with the Reddit API client.
 * Token state is guarded by [tokenLock] so concurrent requests don't race on refresh.
 */
@Singleton
class RedditAuthInterceptor @Inject constructor(
    private val userKeyCache: UserKeyCache,
    private val tokenClient: OkHttpClient,
) : Interceptor {

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenExpiryMs: Long = 0L
    private val tokenLock = Any()

    override fun intercept(chain: Interceptor.Chain): Response {
        val clientId = userKeyCache.effectiveRedditClientId
        if (clientId.isBlank()) {
            // Not configured — pass through without auth (will likely 403, handled by pager)
            return chain.proceed(chain.request())
        }

        val token = getValidToken(clientId)
        val request = buildRequest(chain.request(), token)
        val response = chain.proceed(request)

        // On 401: invalidate token and retry once with a fresh one
        if (response.code == 401) {
            response.close()
            synchronized(tokenLock) {
                cachedToken = null
                tokenExpiryMs = 0L
            }
            val retryToken = getValidToken(clientId)
            return chain.proceed(buildRequest(chain.request(), retryToken))
        }

        return response
    }

    private fun buildRequest(original: Request, token: String?): Request =
        original.newBuilder()
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .header("User-Agent", USER_AGENT)
            .build()

    private fun getValidToken(clientId: String): String? {
        synchronized(tokenLock) {
            val now = System.currentTimeMillis()
            val cached = cachedToken
            // Treat tokens as valid up to 60 s before expiry to avoid edge-case 401s
            if (cached != null && now < tokenExpiryMs - 60_000L) return cached
            return fetchToken(clientId)
        }
    }

    private fun fetchToken(clientId: String): String? {
        val deviceId = userKeyCache.effectiveRedditDeviceId
        val body = FormBody.Builder()
            .add("grant_type", "https://oauth.reddit.com/grants/installed_client")
            .add("device_id", deviceId)
            .build()
        val request = Request.Builder()
            .url("https://www.reddit.com/api/v1/access_token")
            .post(body)
            .header("Authorization", Credentials.basic(clientId, ""))
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            tokenClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Token fetch HTTP ${resp.code}")
                    return null
                }
                val json = resp.body?.string() ?: return null
                // Simple string extraction — avoids another Json instance dependency
                val token = extractJsonString(json, "access_token")
                val expiresIn = extractJsonLong(json, "expires_in") ?: 3_600L
                if (token.isNullOrBlank()) {
                    Log.w(TAG, "No access_token in response")
                    return null
                }
                cachedToken = token
                tokenExpiryMs = System.currentTimeMillis() + expiresIn * 1_000L
                Log.d(TAG, "Reddit token acquired (expires in ${expiresIn}s)")
                token
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token fetch failed", e)
            null
        }
    }

    private fun extractJsonString(json: String, key: String): String? {
        val marker = "\"$key\":\""
        val start = json.indexOf(marker).takeIf { it >= 0 } ?: return null
        val valueStart = start + marker.length
        val end = json.indexOf('"', valueStart).takeIf { it >= 0 } ?: return null
        return json.substring(valueStart, end).takeIf { it.isNotBlank() }
    }

    private fun extractJsonLong(json: String, key: String): Long? {
        val marker = "\"$key\":"
        val start = json.indexOf(marker).takeIf { it >= 0 } ?: return null
        val valueStart = start + marker.length
        val digits = json.substring(valueStart).takeWhile { it.isDigit() }
        return digits.toLongOrNull()
    }

    companion object {
        private const val TAG = "RedditAuthInterceptor"
        const val USER_AGENT = "android:com.wallora.app:v1.0 (by /u/wallora_app)"
    }
}
