package app.geeklab.audiencelab.sdk.internal.network

import app.geeklab.audiencelab.sdk.internal.device.DeviceInfo
import app.geeklab.audiencelab.sdk.internal.identity.IdentitySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FetchTokenRequestMapperTest {
    @Test
    fun map_buildsRequestWithDeviceAndIdentityFields() {
        val payload = FetchTokenRequestMapper.map(
            FetchTokenBuildInput(
                sdkVersion = "1.1.0",
                sdkType = "native_android",
                appVersion = "2.5.1",
                createdAtMillis = 1_710_000_000_000L,
                developmentMode = false,
                whitelistedProperties = mapOf("campaign" to "summer"),
                blacklistedProperties = mapOf("external_id" to "user-123"),
                deviceInfo = DeviceInfo(
                    deviceName = "Pixel 8",
                    deviceModel = "Pixel 8",
                    osSystem = "Android 14",
                    dpi = 420,
                    width = 1080,
                    height = 2400,
                    timezone = "Europe/Helsinki",
                    utcOffsetHours = 2.0,
                    lowBatteryLevel = true,
                    glEsVersion = "3.2"
                ),
                identity = IdentitySnapshot(
                    ga = "gaid-123",
                    asid = "asid-456",
                    aid = "aid-789",
                    lat = true
                )
            )
        )

        assertEquals("device-metrics", payload["type"])
        assertEquals("2024-03-09 16:00:00", payload["created_at"])
        val data = payload["data"] as Map<*, *>
        assertEquals("Android 14", data["os_system"])
        assertEquals("Pixel 8", data["device_name"])
        assertEquals(420, data["dpi"])
        assertEquals(1080, data["window_width"])
        assertEquals(2400, data["window_height"])
        assertEquals("Europe/Helsinki", data["timezone"])
        assertEquals(true, data["low_battery_level"])
        assertEquals("3.2", data["gpu_version"])

        assertEquals("gaid-123", payload["ga"])
        assertEquals("asid-456", payload["asid"])
        assertEquals("aid-789", payload["aid"])
        assertEquals(true, payload["lat"])
        assertTrue(payload.containsKey("wp"))
        assertTrue(payload.containsKey("bp"))
    }
}
