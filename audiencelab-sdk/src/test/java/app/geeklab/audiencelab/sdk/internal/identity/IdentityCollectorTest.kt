package app.geeklab.audiencelab.sdk.internal.identity

import android.content.ContextWrapper
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.util.concurrent.atomic.AtomicInteger
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

    @Test
    fun collectDetailed_memoizesProviderValues_untilCacheIsCleared() {
        IdentityCollector.clearProviderCache()
        val advertisingProviderCalls = AtomicInteger(0)
        val appSetProviderCalls = AtomicInteger(0)

        val first = IdentityCollector.collectDetailed(
            context = context,
            advertisingIdProvider = {
                advertisingProviderCalls.incrementAndGet()
                app.geeklab.audiencelab.sdk.AdvertisingInfo(
                    advertisingId = "gaid-provider-123",
                    limitAdTracking = false
                )
            },
            appSetIdProvider = {
                appSetProviderCalls.incrementAndGet()
                "asid-provider-456"
            },
            overrides = IdentityOverrides(),
            allowAutoResolve = true,
            googlePlayAutoResolveEnabled = true
        )

        val second = IdentityCollector.collectDetailed(
            context = context,
            advertisingIdProvider = {
                advertisingProviderCalls.incrementAndGet()
                app.geeklab.audiencelab.sdk.AdvertisingInfo(
                    advertisingId = "gaid-provider-999",
                    limitAdTracking = true
                )
            },
            appSetIdProvider = {
                appSetProviderCalls.incrementAndGet()
                "asid-provider-999"
            },
            overrides = IdentityOverrides(),
            allowAutoResolve = true,
            googlePlayAutoResolveEnabled = true
        )

        assertEquals(1, advertisingProviderCalls.get())
        assertEquals(1, appSetProviderCalls.get())
        assertEquals("gaid-provider-123", first.snapshot.ga)
        assertEquals("asid-provider-456", first.snapshot.asid)
        assertEquals(false, first.snapshot.lat)
        assertEquals("gaid-provider-123", second.snapshot.ga)
        assertEquals("asid-provider-456", second.snapshot.asid)
        assertEquals(false, second.snapshot.lat)

        IdentityCollector.clearProviderCache()

        val third = IdentityCollector.collectDetailed(
            context = context,
            advertisingIdProvider = {
                advertisingProviderCalls.incrementAndGet()
                app.geeklab.audiencelab.sdk.AdvertisingInfo(
                    advertisingId = "gaid-provider-999",
                    limitAdTracking = true
                )
            },
            appSetIdProvider = {
                appSetProviderCalls.incrementAndGet()
                "asid-provider-999"
            },
            overrides = IdentityOverrides(),
            allowAutoResolve = true,
            googlePlayAutoResolveEnabled = true
        )

        assertEquals(2, advertisingProviderCalls.get())
        assertEquals(2, appSetProviderCalls.get())
        assertEquals("gaid-provider-999", third.snapshot.ga)
        assertEquals("asid-provider-999", third.snapshot.asid)
        assertEquals(true, third.snapshot.lat)
    }
}
