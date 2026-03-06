package app.geeklab.audiencelab.sdk.internal.identity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityCollectorTest {
    @Test
    fun collect_usesOverrides_whenContextIsUnavailable() {
        val snapshot = IdentityCollector.collect(
            context = null,
            advertisingIdProvider = null,
            appSetIdProvider = null,
            overrides = IdentityOverrides(
                ga = "gaid-123",
                asid = "asid-456",
                lat = true
            )
        )

        assertEquals("gaid-123", snapshot.ga)
        assertEquals("asid-456", snapshot.asid)
        assertEquals(true, snapshot.lat)
        assertNull(snapshot.aid)
    }
}
