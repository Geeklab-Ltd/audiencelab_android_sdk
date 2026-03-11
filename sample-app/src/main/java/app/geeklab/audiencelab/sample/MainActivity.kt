package app.geeklab.audiencelab.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import app.geeklab.audiencelab.sdk.AudienceLabOptions
import app.geeklab.audiencelab.sdk.AudienceLabSDK
import app.geeklab.audiencelab.sdk.RequestDebugEntry
import app.geeklab.audiencelab.sdk.UserPropertyScope

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val initialized = AudienceLabSDK.initialize(
            context = applicationContext,
            apiKey = "Geekac39a4bf-588f-4094-8ad8-2325eb96136f",
            options = AudienceLabOptions(
                isDevelopmentMode = BuildConfig.DEBUG,
                isDebugEnabled = BuildConfig.DEBUG
            )
        )

        AudienceLabSDK.setRequestDebugListener { entry: RequestDebugEntry ->
            RequestLogStore.add(entry)
            runOnUiThread { renderStatus(initialized) }
        }

        AudienceLabSDK.setUserProperty(
            key = "country",
            value = "FI",
            scope = UserPropertyScope.WHITELISTED
        )
        AudienceLabSDK.setUserProperty(
            key = "player_tier",
            value = "beta",
            scope = UserPropertyScope.WHITELISTED
        )
        AudienceLabSDK.setUserProperty(
            key = "internal_segment",
            value = "testers",
            scope = UserPropertyScope.BLACKLISTED
        )
        AudienceLabSDK.setUserProperty(
            key = "device_bucket",
            value = "lab",
            scope = UserPropertyScope.BLACKLISTED
        )

        findViewById<Button>(R.id.send_purchase_button).setOnClickListener {
            AudienceLabSDK.sendPurchaseEvent(
                itemId = "starter.pack",
                itemName = "Starter Pack",
                value = 4.99,
                currency = "USD",
                status = "success",
                transactionId = "sample-trx-${System.currentTimeMillis()}"
            )
            renderStatus(initialized)
        }

        findViewById<Button>(R.id.send_ad_button).setOnClickListener {
            AudienceLabSDK.sendAdEvent(
                adId = "rewarded.video.1",
                name = "rewarded_video",
                source = "sample_network",
                watchTime = 30.0,
                reward = true,
                mediaSource = "sample_campaign",
                channel = "sample_channel",
                value = 0.75,
                currency = "USD"
            )
            renderStatus(initialized)
        }

        findViewById<Button>(R.id.send_custom_button).setOnClickListener {
            AudienceLabSDK.sendCustomEvent(
                name = "sample_custom_event",
                params = mapOf(
                    "source" to "sample-app",
                    "button" to "send_custom_button"
                )
            )
            renderStatus(initialized)
        }

        findViewById<Button>(R.id.open_request_log_button).setOnClickListener {
            startActivity(Intent(this, RequestLogActivity::class.java))
        }

        findViewById<Button>(R.id.open_queue_log_button).setOnClickListener {
            startActivity(Intent(this, QueueLogActivity::class.java))
        }

        renderStatus(initialized)
    }

    override fun onDestroy() {
        super.onDestroy()
        AudienceLabSDK.clearRequestDebugListener()
    }

    private fun renderStatus(initialized: Boolean) {
        val token = AudienceLabSDK.getTokenDebugSnapshot()
        val identity = AudienceLabSDK.getIdentityDebugSnapshot()
        findViewById<TextView>(R.id.sdk_status).text =
            "AudienceLab SDK initialized: $initialized\n" +
                "Version: ${AudienceLabSDK.getSdkVersion()}\n" +
                "Dev mode: ${AudienceLabSDK.isDevelopmentMode()}\n" +
                "Creative token: ${token.creativeTokenPreview ?: "none"} (${token.lastFetchStatus})\n" +
                "Identity settled: ${identity.settled}\n" +
                "Queued events: ${AudienceLabSDK.getQueueSize()}\n" +
                "Logged requests: ${RequestLogStore.snapshot().size}\n" +
                "Whitelisted props: ${AudienceLabSDK.getWhitelistedUserProperties()}\n" +
                "Blacklisted props: ${AudienceLabSDK.getBlacklistedUserProperties()}"
    }
}
