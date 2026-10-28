package app.geeklab.audiencelab.sdk.internal.storage

import android.content.Context
import java.io.File
import java.util.UUID

internal fun interface InstallInstanceIdStore {
    fun getOrCreate(): String
}

internal class NoBackupInstallInstanceIdStore(
    private val directory: File,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) : InstallInstanceIdStore {
    constructor(context: Context) : this(context.noBackupFilesDir)

    @Synchronized
    override fun getOrCreate(): String {
        val file = File(directory, FILE_NAME)
        val existing = runCatching { file.readText() }
            .getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        if (existing != null) {
            return existing
        }

        val generated = idFactory().trim()
        require(generated.isNotEmpty()) { "Install instance ID factory returned an empty value" }
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create no-backup storage directory"
        }
        file.writeText(generated)
        return generated
    }

    private companion object {
        const val FILE_NAME = "audiencelab-install-instance-id"
    }
}
