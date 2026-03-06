package app.geeklab.audiencelab.sample

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import app.geeklab.audiencelab.sdk.AudienceLabOptions
import app.geeklab.audiencelab.sdk.AudienceLabSDK

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val initialized = AudienceLabSDK.initialize(
            context = applicationContext,
            apiKey = "sample-api-key",
            options = AudienceLabOptions(isDebugEnabled = true)
        )

        findViewById<TextView>(R.id.sdk_status).text =
            "AudienceLab SDK initialized: $initialized\nVersion: ${AudienceLabSDK.getSdkVersion()}"
    }
}
