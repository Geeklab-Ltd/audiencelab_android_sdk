package app.geeklab.audiencelab.sdk.internal.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestDebugSanitizerTest {
    @Test
    fun requestBody_hidesEntireLifecycleRequest() {
        val debugBody = RequestDebugSanitizer.requestBody(
            path = Endpoints.FETCH_TOKEN,
            body = mapOf(
                "creativeToken" to "token-secret",
                "x1" to "instance-secret",
                "data" to mapOf("device_model" to "Pixel")
            )
        )

        assertEquals("{\"redacted\":true}", debugBody)
        assertFalse(debugBody.contains("token-secret"))
        assertFalse(debugBody.contains("instance-secret"))
    }

    @Test
    fun requestBody_redactsLifecycleIdentifiersFromOrdinaryEvents() {
        val debugBody = RequestDebugSanitizer.requestBody(
            path = Endpoints.WEBHOOK,
            body = mapOf(
                "creativeToken" to "token-secret",
                "x7" to "install-secret",
                "type" to "custom"
            )
        )

        assertTrue(debugBody.contains("[REDACTED]"))
        assertFalse(debugBody.contains("token-secret"))
        assertFalse(debugBody.contains("install-secret"))
    }
}
