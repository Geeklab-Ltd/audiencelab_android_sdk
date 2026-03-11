package app.geeklab.audiencelab.sdk.internal.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetryPolicyTest {
    @Test
    fun backoffMillis_growsExponentially_andCaps() {
        assertEquals(2_000L, RetryPolicy.backoffMillis(1))
        assertEquals(4_000L, RetryPolicy.backoffMillis(2))
        assertEquals(8_000L, RetryPolicy.backoffMillis(3))
        assertEquals(300_000L, RetryPolicy.backoffMillis(99))
    }

    @Test
    fun tokenBackoffMillis_usesParityBaseline_andCapsWithJitter() {
        assertEquals(5_000L, RetryPolicy.tokenBackoffMillis(attempt = 1, jitterRatio = 0.0))
        assertEquals(10_000L, RetryPolicy.tokenBackoffMillis(attempt = 2, jitterRatio = 0.0))
        assertEquals(6_500L, RetryPolicy.tokenBackoffMillis(attempt = 1, jitterRatio = 0.3))
        assertEquals(300_000L, RetryPolicy.tokenBackoffMillis(attempt = 99, jitterRatio = 0.3))
        assertTrue(RetryPolicy.canRetryToken(9))
        assertFalse(RetryPolicy.canRetryToken(10))
    }

    @Test
    fun isRetryable_classifiesNetworkErrors() {
        assertTrue(RetryPolicy.isRetryable(NetworkError.TransportFailure(IOException("offline"))))
        assertTrue(RetryPolicy.isRetryable(NetworkError.HttpFailure(500, emptyMap())))
        assertTrue(RetryPolicy.isRetryable(NetworkError.HttpFailure(429, emptyMap())))
        assertTrue(RetryPolicy.isRetryable(NetworkError.HttpFailure(408, emptyMap())))

        assertFalse(RetryPolicy.isRetryable(NetworkError.ApiKeyMissing))
        assertFalse(RetryPolicy.isRetryable(NetworkError.InvalidUrl("bad")))
        assertFalse(RetryPolicy.isRetryable(NetworkError.HttpFailure(400, emptyMap())))
        assertFalse(RetryPolicy.isRetryable(NetworkError.HttpFailure(404, emptyMap())))
        assertFalse(RetryPolicy.isRetryable(NetworkError.SerializationFailure("bad json")))
    }
}
