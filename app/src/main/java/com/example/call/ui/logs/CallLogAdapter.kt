package com.example.call.ui.logs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.data.CallLogEntity
import com.example.call.databinding.ItemCallLogBinding
import com.example.call.databinding.ItemCallLogTimelineBinding
import com.example.call.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class CallLogAdapter(
    private val onCallClick: (CallLogEntity) -> Unit,
    private val onContactClick: (CallLogEntity) -> Unit,
    private val onTagLongPress: (CallLogEntity) -> Unit
) : ListAdapter<CallLogAdapter.LogItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private var timelineMode: Boolean = false

    sealed class LogItem {
        data class Divider(val date: String) : LogItem()
        data class Entry(val log: CallLogEntity) : LogItem()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is LogItem.Divider -> TYPE_DIVIDER
            is LogItem.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DIVIDER -> {
                val view = inflater.inflate(R.layout.item_call_log_divider, parent, false)
                DividerViewHolder(view)
            }
            else -> {
                if (timelineMode) {
                    val binding = ItemCallLogTimelineBinding.inflate(inflater, parent, false)
                    TimelineLogViewHolder(binding, onCallClick, onContactClick)
                } else {
                    val binding = ItemCallLogBinding.inflate(inflater, parent, false)
                    LogViewHolder(binding, onCallClick, onContactClick)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is DividerViewHolder && item is LogItem.Divider) {
            holder.bind(item.date)
        } else if (holder is LogViewHolder && item is LogItem.Entry) {
            holder.bind(item.log)
        } else if (holder is TimelineLogViewHolder && item is LogItem.Entry) {
            holder.bind(item.log)
        }
    }

    fun setTimelineMode(enabled: Boolean) {
        if (timelineMode == enabled) return
        timelineMode = enabled
        notifyDataSetChanged()
    }

    fun submitLogs(logs: List<CallLogEntity>) {
        val items = mutableListOf<LogItem>()
        var lastDate = ""
        val today = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        val yesterday = getYesterdayDateString()

        logs.forEach { log ->
            val date = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(log.timestamp))
            if (date != lastDate) {
                val label = when (date) {
                    today -> "Today"
                    yesterday -> "Yesterday"
                    else -> SimpleDateFormat("MMMM d", Locale.getDefault()).format(Date(log.timestamp))
                }
                items.add(LogItem.Divider(label))
                lastDate = date
            }
            items.add(LogItem.Entry(log))
        }
        submitList(items)
    }

    private fun getYesterdayDateString(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -1)
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
    }

    class DividerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view.findViewById<TextView>(R.id.dividerText)
        fun bind(date: String) {
            textView.text = date
        }
    }

    inner class LogViewHolder(
        private val binding: ItemCallLogBinding,
        private val onCallClick: (CallLogEntity) -> Unit,
        private val onContactClick: (CallLogEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: CallLogEntity) {
            bindCommon(
                context = binding.root.context,
                rowRoot = binding.root,
                avatarContainer = binding.avatarContainer,
                callerName = binding.callerName,
                callerNumber = binding.callerNumber,
                tagLabel = binding.tagLabel,
                callTime = binding.callTime,
                callDirection = binding.callDirection,
                item = item,
                onCallClick = onCallClick,
                onContactClick = onContactClick,
                onTagLongPress = onTagLongPress
            )
        }
    }

    inner class TimelineLogViewHolder(
        private val binding: ItemCallLogTimelineBinding,
        private val onCallClick: (CallLogEntity) -> Unit,
        private val onContactClick: (CallLogEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun bind(item: CallLogEntity) {
            bindCommon(
                context = binding.root.context,
                rowRoot = binding.root,
                avatarContainer = binding.avatarContainer,
                callerName = binding.callerName,
                callerNumber = binding.callerNumber,
                tagLabel = binding.tagLabel,
                callTime = binding.callTime,
                callDirection = binding.callDirection,
                item = item,
                onCallClick = onCallClick,
                onContactClick = onContactClick,
                onTagLongPress = onTagLongPress,
                timeFormat = timeFormat
            )
        }
    }

    companion object {
        private const val TYPE_DIVIDER = 0
        private const val TYPE_ENTRY = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LogItem>() {
            override fun areItemsTheSame(oldItem: LogItem, newItem: LogItem): Boolean {
                return if (oldItem is LogItem.Entry && newItem is LogItem.Entry) {
                    oldItem.log.id == newItem.log.id
                } else if (oldItem is LogItem.Divider && newItem is LogItem.Divider) {
                    oldItem.date == newItem.date
                } else false
            }

            override fun areContentsTheSame(oldItem: LogItem, newItem: LogItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    private fun bindCommon(
        context: android.content.Context,
        rowRoot: android.view.View,
        avatarContainer: android.view.View,
        callerName: TextView,
        callerNumber: TextView,
        tagLabel: TextView,
        callTime: TextView,
        callDirection: android.widget.ImageView,
        item: CallLogEntity,
        onCallClick: (CallLogEntity) -> Unit,
        onContactClick: (CallLogEntity) -> Unit,
        onTagLongPress: (CallLogEntity) -> Unit,
        timeFormat: SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    ) {
        val fallbackName = if (item.phoneNumber.isBlank()) {
            context.getString(R.string.unknown_caller)
        } else {
            item.phoneNumber
        }

        callerName.text = item.displayName ?: fallbackName
        callerNumber.text = item.phoneNumber
        callTime.text = timeFormat.format(Date(item.timestamp))
        val tagText = CallLogTags.label(context, item.tag)
        if (tagText.isNullOrBlank()) {
            tagLabel.text = ""
            tagLabel.visibility = android.view.View.GONE
        } else {
            tagLabel.text = tagText
            tagLabel.visibility = android.view.View.VISIBLE
        }

        val iconRes = when (item.direction.lowercase(Locale.ROOT)) {
            "incoming" -> android.R.drawable.sym_call_incoming
            "outgoing" -> android.R.drawable.sym_call_outgoing
            "missed" -> android.R.drawable.sym_call_missed
            else -> android.R.drawable.sym_call_incoming
        }
        callDirection.setImageResource(iconRes)

        if (item.direction.lowercase(Locale.ROOT) == "missed") {
            callerName.setTextColor(ContextCompat.getColor(context, R.color.call_red))
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            callerName.setTextColor(typedValue.data)
        }

        rowRoot.setOnClickListener { onCallClick(item) }
        rowRoot.setOnLongClickListener {
            onTagLongPress(item)
            true
        }
        avatarContainer.setOnClickListener { onContactClick(item) }
    }
}
