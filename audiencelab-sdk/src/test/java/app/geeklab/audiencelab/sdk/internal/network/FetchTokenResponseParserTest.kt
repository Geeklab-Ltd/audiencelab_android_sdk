package app.geeklab.audiencelab.sdk.internal.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FetchTokenResponseParserTest {
    @Test
    fun parse_preservesLegacyResponseFields() {
        val response = FetchTokenResponseParser.parse(
            mapOf(
                "token" to "token-123",
                "message" to "matched",
                "wp" to mapOf("campaign" to "spring", "priority" to 2)
            )
        )

        assertEquals("token-123", response.token)
        assertEquals("matched", response.message)
        assertEquals(mapOf("campaign" to "spring", "priority" to "2"), response.whitelistedProperties)
        assertNull(response.installId)
        assertNull(response.decisionCode)
        assertNull(response.refreshAfter)
    }

    @Test
    fun parse_readsLifecycleV2FieldsAsOpenStrings() {
        val response = FetchTokenResponseParser.parse(
            mapOf(
                "token" to "token-456",
                "x7" to " install-123 ",
                "x9" to "inherited",
                "xm" to "2026-11-27T12:30:00Z"
            )
        )

        assertEquals("install-123", response.installId)
        assertEquals("inherited", response.decisionCode)
        assertEquals("2026-11-27T12:30:00Z", response.refreshAfter)
    }
}
