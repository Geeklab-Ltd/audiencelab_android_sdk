package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.internal.device.DeviceInfo
import app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventEnvelopeMapperTest {
    @Test
    fun map_normalizesAliasTypes_andIncludesCanonicalFields() {
        val envelope = EventEnvelopeMapper.map(
            EnvelopeBuildInput(
                type = "custom.ad",
                payload = mapOf("ad_id" to "placement_1"),
                creativeToken = "token-123",
                retentionDay = 5,
                sdkVersion = "1.1.0",
                sdkType = "native_android",
                appVersion = "2.5.1",
                developmentMode = false,
                createdAtMillis = 1_700_000_000_000L,
                dedupeKey = "dk-1",
                whitelistedProperties = mapOf("campaign" to "summer"),
                blacklistedProperties = mapOf("external_id" to "abc123"),
                deviceInfo = DeviceInfo(
                    deviceName = "Pixel 8",
                    deviceModel = "Pixel 8",
                    osSystem = "Android 14",
                    dpi = 420,
                    width = 1080,
                    height = 2400,
                    timezone = "Europe/Helsinki",
                    utcOffsetHours = 2.0,
                    lowBatteryLevel = false,
                    glEsVersion = "3.2"
                ),
                identity = IdentitySnapshot(
                    ga = "gaid-123",
                    asid = "asid-456",
                    aid = "aid-789",
                    lat = false
                ),
                eventId = "evt_test_1"
            )
        )

        assertEquals("ad", envelope["type"])
        assertEquals("token-123", envelope["creativeToken"])
        assertEquals("evt_test_1", envelope["eid"])
        assertEquals("2023-11-14 22:13:20", envelope["created_at"])
        assertEquals(5, envelope["retention_day"])
        assertEquals("gaid-123", envelope["ga"])
        assertEquals("asid-456", envelope["asid"])
        assertEquals("aid-789", envelope["aid"])
        assertEquals(false, envelope["lat"])
        assertEquals("dk-1", envelope["dk"])
        assertEquals("native_android", envelope["sdk_type"])
        assertTrue(envelope.containsKey("wp"))
        assertTrue(envelope.containsKey("bp"))
    }

    @Test
    fun map_omitsOptionalIdentityAndPropertyFields_whenNotProvided() {
        val envelope = EventEnvelopeMapper.map(
            EnvelopeBuildInput(
                type = "custom",
                payload = mapOf("en" to "level_complete"),
                creativeToken = "organic#gp",
                retentionDay = null,
                sdkVersion = "1.1.0",
                sdkType = "native_android",
                appVersion = "unknown",
                developmentMode = true,
                createdAtMillis = 1_700_000_000_000L,
                dedupeKey = null,
                whitelistedProperties = emptyMap(),
                blacklistedProperties = emptyMap(),
                deviceInfo = DeviceInfo(
                    deviceName = "unknown",
                    deviceModel = "unknown",
                    osSystem = "Android unknown",
                    dpi = 0,
                    width = 0,
                    height = 0,
                    timezone = "UTC",
                    utcOffsetHours = 0.0,
                    lowBatteryLevel = null,
                    glEsVersion = null
                ),
                identity = IdentitySnapshot(
                    ga = null,
                    asid = null,
                    aid = null,
                    lat = null
                ),
                eventId = "evt_test_2"
            )
        )

        assertEquals("custom", envelope["type"])
        assertFalse(envelope.containsKey("ga"))
        assertFalse(envelope.containsKey("asid"))
        assertFalse(envelope.containsKey("aid"))
        assertFalse(envelope.containsKey("lat"))
        assertFalse(envelope.containsKey("dk"))
        assertFalse(envelope.containsKey("wp"))
        assertFalse(envelope.containsKey("bp"))
        assertFalse(envelope.containsKey("retention_day"))
    }
}
