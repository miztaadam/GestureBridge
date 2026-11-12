package com.gesturebridge.net

import com.gesturebridge.BuildConfig
import com.gesturebridge.Trigger
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

/** Outcome of one webhook call. */
sealed interface WebhookResult {
    /** The server answered with a 2xx status. */
    data class Success(val status: Int) : WebhookResult

    /** The server answered, but not with 2xx (401 = bad token, 404 = wrong URL, ...). */
    data class HttpError(val status: Int) : WebhookResult

    /** No answer at all: no route to the server, timeout, bad URL, DNS failure. */
    data class NetworkError(val message: String) : WebhookResult

    val isSuccess: Boolean get() = this is Success

    /** Short text for the watch screen. */
    val summary: String
        get() = when (this) {
            is Success -> "OK ($status)"
            is HttpError -> "HTTP $status"
            is NetworkError -> message
        }
}

/**
 * Lightweight singleton that sends the HTTP POST.
 *
 * One [HttpClient] is created on first use and reused for every request, so
 * repeat presses skip the setup cost. All calls are `suspend` and never block
 * the calling thread.
 */
object WebhookClient {

    // =====================================================================
    //  >>> HOME ASSISTANT BEARER TOKENS <<<
    //
    //  Do NOT paste a token into this file. Put it in local.properties in
    //  the project's root folder (copy local.properties.example to start):
    //
    //      GESTURE_BEARER_TOKEN=eyJhbGciOi...   <- sent with the Gesture 1 URL
    //      BUTTON_BEARER_TOKEN=eyJhbGciOi...    <- sent with the Action Button URL
    //
    //  The build copies those two values into BuildConfig, which is read
    //  below. local.properties is git-ignored, so the tokens stay off GitHub.
    //
    //  Leave a token empty when its URL needs none (Home Assistant
    //  /api/webhook/<id> URLs and Tasker do not); no Authorization header
    //  is sent in that case.
    // =====================================================================
    private fun bearerTokenFor(trigger: Trigger): String = when (trigger) {
        Trigger.GESTURE -> BuildConfig.GESTURE_BEARER_TOKEN
        Trigger.ACTION_BUTTON -> BuildConfig.BUTTON_BEARER_TOKEN
    }

    private val client: HttpClient by lazy {
        HttpClient(Android) {
            // A watch press should succeed or fail fast, not hang.
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 8_000
                socketTimeoutMillis = 8_000
            }
            // Non-2xx answers are reported as HttpError instead of thrown.
            expectSuccess = false
        }
    }

    /**
     * POSTs a small JSON body to [url] for [trigger].
     *
     * Body: `{"source":"gesturebridge","trigger":"action_button","timestamp":1731400000000}`
     * In a Home Assistant webhook automation it is available as `trigger.json`.
     */
    suspend fun post(url: String, trigger: Trigger): WebhookResult =
        post(url, trigger.id, bearerTokenFor(trigger))

    /** The request itself, with the token passed in so it can be tested. */
    internal suspend fun post(url: String, triggerId: String, token: String): WebhookResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return WebhookResult.NetworkError("URL must start with http:// or https://")
        }
        val body = """{"source":"gesturebridge","trigger":"$triggerId","timestamp":${System.currentTimeMillis()}}"""

        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                if (token.isNotBlank()) {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }
                setBody(body)
            }
            if (response.status.isSuccess()) {
                WebhookResult.Success(response.status.value)
            } else {
                WebhookResult.HttpError(response.status.value)
            }
        } catch (e: CancellationException) {
            throw e // never swallow coroutine cancellation
        } catch (e: Exception) {
            WebhookResult.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }
}
