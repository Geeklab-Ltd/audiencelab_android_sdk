package app.geeklab.audiencelab.sdk.internal.storage

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class InstallInstanceStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun getOrCreate_persistsOneIdInTheNoBackupDirectory() {
        val directory = temporaryFolder.newFolder("no-backup")
        val factoryCalls = AtomicInteger(0)
        val firstStore = NoBackupInstallInstanceIdStore(directory) {
            factoryCalls.incrementAndGet()
            "instance-123"
        }

        assertEquals("instance-123", firstStore.getOrCreate())
        assertEquals("instance-123", firstStore.getOrCreate())
        assertEquals(
            "instance-123",
            NoBackupInstallInstanceIdStore(directory) { "unexpected" }.getOrCreate()
        )
        assertEquals(1, factoryCalls.get())
    }
}
