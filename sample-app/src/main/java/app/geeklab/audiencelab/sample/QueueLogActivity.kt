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
import app.geeklab.audiencelab.sdk.QueueDebugEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class QueueLogActivity : Activity() {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_queue_log)
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.queue_log_summary).text = buildSummary()
        val listView = findViewById<ListView>(R.id.queue_log_list)
        val emptyView = findViewById<TextView>(R.id.queue_log_empty)
        listView.emptyView = emptyView
        listView.adapter = QueueLogAdapter(
            inflater = LayoutInflater.from(this),
            entries = AudienceLabSDK.getQueueDebugSnapshot(),
            formatter = formatter
        )
    }

    private fun buildSummary(): String {
        val token = AudienceLabSDK.getTokenDebugSnapshot()
        return buildString {
            appendLine("queue_size=${AudienceLabSDK.getQueueSize()}")
            appendLine("dev_mode=${AudienceLabSDK.isDevelopmentMode()}")
            appendLine("token_status=${token.lastFetchStatus}")
            append("token=${token.creativeTokenPreview ?: "none"}")
        }.trim()
    }
}

private class QueueLogAdapter(
    private val inflater: LayoutInflater,
    private val entries: List<QueueDebugEntry>,
    private val formatter: SimpleDateFormat
) : BaseAdapter() {
    override fun getCount(): Int = entries.size

    override fun getItem(position: Int): QueueDebugEntry = entries[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.row_queue_log, parent, false)
        val entry = getItem(position)

        view.setBackgroundColor(
            if (position % 2 == 0) Color.parseColor("#FFF8E8")
            else Color.parseColor("#FFF1CF")
        )

        view.findViewById<TextView>(R.id.queue_request_name).text = entry.requestName
        view.findViewById<TextView>(R.id.queue_event_id).text = "Event ID: ${entry.eventId}"
        view.findViewById<TextView>(R.id.queue_attempts).text =
            "Attempts: ${entry.attemptCount}  Next: ${formatMillis(entry.nextAttemptAtMillis)}"
        view.findViewById<TextView>(R.id.queue_queued_at).text =
            "Queued: ${formatMillis(entry.queuedAtMillis)}"
        view.findViewById<TextView>(R.id.queue_created_at).text =
            "Created: ${formatMillis(entry.createdAtMillis)}"
        view.findViewById<TextView>(R.id.queue_payload).text = entry.payload

        return view
    }

    private fun formatMillis(timestampMillis: Long): String {
        if (timestampMillis <= 0L) {
            return "immediate"
        }
        return formatter.format(Date(timestampMillis))
    }
}
