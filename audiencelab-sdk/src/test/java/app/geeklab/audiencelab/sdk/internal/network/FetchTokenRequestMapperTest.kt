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
                developmentMode = false,
                whitelistedProperties = mapOf("campaign" to "summer"),
                deviceInfo = DeviceInfo(
                    deviceName = "Pixel 8",
                    deviceModel = "Pixel 8",
                    osSystem = "Android 14",
                    width = 1080,
                    height = 2400,
                    timezone = "Europe/Helsinki",
                    utcOffsetHours = 2.0
                ),
                identity = IdentitySnapshot(
                    ga = "gaid-123",
                    asid = "asid-456",
                    aid = "aid-789",
                    lat = true
                )
            )
        )

        val data = payload["data"] as Map<*, *>
        assertEquals("Android 14", data["os_system"])
        assertEquals("Pixel 8", data["device_name"])
        assertEquals(1080, data["width"])
        assertEquals(2400, data["height"])
        assertEquals("Europe/Helsinki", data["timezone"])

        assertEquals("gaid-123", payload["ga"])
        assertEquals("asid-456", payload["asid"])
        assertEquals("aid-789", payload["aid"])
        assertEquals(true, payload["lat"])
        assertTrue(payload.containsKey("wp"))
    }
}
