package app.geeklab.audiencelab.sdk.internal.identity

import android.content.ContextWrapper
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityCollectorTest {
    private val context = object : ContextWrapper(null) {
        override fun getPackageName(): String = "app.geeklab.audiencelab.test"
    }

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
            ),
            googlePlayAutoResolveEnabled = true
        )

        assertEquals("gaid-123", snapshot.ga)
        assertEquals("asid-456", snapshot.asid)
        assertEquals(true, snapshot.lat)
        assertNull(snapshot.aid)
    }

    @Test
    fun collectDetailed_marksIdentitySettled_whenContextIsUnavailable() {
        val result = IdentityCollector.collectDetailed(
            context = null,
            advertisingIdProvider = null,
            appSetIdProvider = null,
            overrides = IdentityOverrides(
                ga = "gaid-123",
                asid = "asid-456",
                lat = true
            ),
            allowAutoResolve = false,
            googlePlayAutoResolveEnabled = true
        )

        assertTrue(result.diagnostics.settled)
        assertTrue(result.diagnostics.androidIdSettled)
        assertTrue(result.diagnostics.advertisingIdSettled)
        assertTrue(result.diagnostics.appSetIdSettled)
    }

    @Test
    fun readAdvertisingInfoReflectively_readsPlayServicesInfo_whenClassesExist() {
        AdvertisingIdClient.advertisingId = "gaid-auto-123"
        AdvertisingIdClient.limitAdTrackingEnabled = true

        val info = PlayServicesIdentityReader.readAdvertisingInfoReflectively(context = null)

        assertEquals("gaid-auto-123", info?.advertisingId)
        assertEquals(true, info?.limitAdTracking)
    }

    @Test
    fun readAppSetIdReflectively_readsPlayServicesInfo_whenClassesExist() {
        AppSet.appSetId = "asid-auto-456"

        val appSetId = PlayServicesIdentityReader.readAppSetIdReflectively(context = null)

        assertEquals("asid-auto-456", appSetId)
    }

    @Test
    fun collectDetailed_marksGoogleIdsSettled_whenAutoResolveDisabled() {
        val result = IdentityCollector.collectDetailed(
            context = context,
            advertisingIdProvider = null,
            appSetIdProvider = null,
            overrides = IdentityOverrides(),
            allowAutoResolve = true,
            googlePlayAutoResolveEnabled = false
        )

        assertTrue(result.diagnostics.settled)
        assertTrue(result.diagnostics.advertisingIdSettled)
        assertTrue(result.diagnostics.appSetIdSettled)
        assertNull(result.snapshot.ga)
        assertNull(result.snapshot.asid)
    }
}
