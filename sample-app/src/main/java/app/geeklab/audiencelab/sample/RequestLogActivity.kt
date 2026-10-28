package app.geeklab.audiencelab.sample

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import app.geeklab.audiencelab.sdk.AudienceLabSDK
import app.geeklab.audiencelab.sdk.RequestDebugEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RequestLogActivity : Activity() {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_log)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.request_log_summary).text = buildSummary()
        val listView = findViewById<ListView>(R.id.request_log_list)
        val emptyView = findViewById<TextView>(R.id.request_log_empty)
        listView.emptyView = emptyView
        listView.adapter = RequestLogAdapter(
            inflater = LayoutInflater.from(this),
            entries = RequestLogStore.snapshot(),
            formatter = formatter
        )
    }

    private fun buildSummary(): String {
        val token = AudienceLabSDK.getTokenDebugSnapshot()
        val identity = AudienceLabSDK.getIdentityDebugSnapshot()
        val device = AudienceLabSDK.getDeviceDebugSnapshot()
        return buildString {
            appendLine("dev_mode=${AudienceLabSDK.isDevelopmentMode()}")
            appendLine("token_status=${token.lastFetchStatus}")
            appendLine("token=${token.creativeTokenPreview ?: "none"} valid=${token.hasValidToken}")
            appendLine("token_attempts=${token.attemptCount} next_retry=${formatNullable(token.nextRetryAtMillis)}")
            appendLine("identity_settled=${identity.settled}")
            appendLine("ga=${identity.advertisingIdPreview ?: "none"} settled=${identity.advertisingIdSettled}")
            appendLine("asid=${identity.appSetIdPreview ?: "none"} settled=${identity.appSetIdSettled}")
            appendLine("aid=${identity.androidIdPreview ?: "none"} settled=${identity.androidIdSettled}")
            append("gles=${device.glEsVersion ?: "unknown"} hardware=${device.hardware ?: "unknown"}")
        }.trim()
    }

    private fun formatNullable(value: Long?): String {
        return if (value == null || value <= 0L) "none" else formatter.format(Date(value))
    }
}

private class RequestLogAdapter(
    private val inflater: LayoutInflater,
    private val entries: List<RequestDebugEntry>,
    private val formatter: SimpleDateFormat
) : BaseAdapter() {
    override fun getCount(): Int = entries.size

    override fun getItem(position: Int): RequestDebugEntry = entries[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.row_request_log, parent, false)
        val entry = getItem(position)

        view.setBackgroundColor(
            if (position % 2 == 0) Color.parseColor("#F7F9FC")
            else Color.parseColor("#EEF3F8")
        )

        view.findViewById<TextView>(R.id.request_name).text = entry.requestName
        view.findViewById<TextView>(R.id.request_meta).text =
            "${formatter.format(Date(entry.timestampMillis))}  ${entry.endpoint}"

        val authView = view.findViewById<TextView>(R.id.request_auth)
        if (entry.authHeaderPreview.isNullOrBlank()) {
            authView.visibility = View.GONE
        } else {
            authView.visibility = View.VISIBLE
            authView.text = "geeklab-api-key: ${entry.authHeaderPreview}"
        }

        val statusView = view.findViewById<TextView>(R.id.request_status)
        statusView.text = if (entry.success) {
            "OK ${entry.responseStatusCode ?: ""}".trim()
        } else {
            "FAIL ${entry.responseStatusCode ?: ""}".trim()
        }
        statusView.setBackgroundColor(
            if (entry.success) Color.parseColor("#D7F5DF")
            else Color.parseColor("#FFD9D6")
        )
        statusView.setTextColor(
            if (entry.success) Color.parseColor("#146C2E")
            else Color.parseColor("#A12A23")
        )

        view.findViewById<TextView>(R.id.request_payload).text = entry.requestBody
        view.findViewById<TextView>(R.id.response_payload).text = entry.responseBody ?: "(empty)"

        val errorView = view.findViewById<TextView>(R.id.request_error)
        if (entry.errorMessage.isNullOrBlank()) {
            errorView.visibility = View.GONE
        } else {
            errorView.visibility = View.VISIBLE
            errorView.text = entry.errorMessage
        }

        return view
    }
}
