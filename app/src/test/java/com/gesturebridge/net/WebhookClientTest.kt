package com.gesturebridge.net

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Sends real HTTP requests from [WebhookClient] to a throwaway server on this machine. */
class WebhookClientTest {

    private class Received(val method: String, val contentType: String?, val authorization: String?, val body: String)

    private lateinit var server: HttpServer
    private var received: Received? = null
    private var respondWith = 200

    private val url get() = "http://127.0.0.1:${server.address.port}/api/webhook/test"

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            received = Received(
                method = exchange.requestMethod,
                contentType = exchange.requestHeaders.getFirst("Content-Type"),
                authorization = exchange.requestHeaders.getFirst("Authorization"),
                body = exchange.requestBody.readBytes().decodeToString(),
            )
            exchange.sendResponseHeaders(respondWith, -1)
            exchange.close()
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    @Test
    fun `posts json with the trigger id and no auth header when the token is empty`() = runBlocking {
        val result = WebhookClient.post(url, "action_button", token = "")

        assertEquals(WebhookResult.Success(200), result)
        val request = received!!
        assertEquals("POST", request.method)
        assertTrue(request.contentType!!.startsWith("application/json"))
        assertNull(request.authorization)
        assertTrue(request.body.startsWith("""{"source":"gesturebridge","trigger":"action_button","timestamp":"""))
    }

    @Test
    fun `sends the bearer token when one is set`() = runBlocking {
        WebhookClient.post(url, "gesture_1", token = "abc123")

        assertEquals("Bearer abc123", received!!.authorization)
    }

    @Test
    fun `a non-2xx answer is reported as an http error`() = runBlocking {
        respondWith = 401

        assertEquals(WebhookResult.HttpError(401), WebhookClient.post(url, "gesture_1", token = "wrong"))
    }

    @Test
    fun `an unreachable server is reported as a network error`() = runBlocking {
        val deadUrl = url
        server.stop(0)

        val result = WebhookClient.post(deadUrl, "gesture_1", token = "")

        assertTrue("was $result", result is WebhookResult.NetworkError)
    }

    @Test
    fun `a url without http or https is rejected without a request`() = runBlocking {
        val result = WebhookClient.post("homeassistant.local/api", "gesture_1", token = "")

        assertTrue(result is WebhookResult.NetworkError)
        assertNull(received)
    }
}
