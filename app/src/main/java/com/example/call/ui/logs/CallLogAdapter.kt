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
import com.example.call.util.ContactLookup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

class CallLogAdapter(
    private val onEntryClick: (CallLogEntity) -> Unit,
    private val onEntryLongPress: (CallLogEntity) -> Unit,
    private val onContactClick: (CallLogEntity) -> Unit,
    private val onTagLongPress: (CallLogEntity) -> Unit,
    private val onQuickCall: (CallLogEntity) -> Unit,
    private val onQuickMessage: (CallLogEntity) -> Unit
) : ListAdapter<CallLogAdapter.LogItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    private var timelineMode: Boolean = false
    private var selectedIds: Set<Long> = emptySet()

    sealed class LogItem {
        data class Divider(val date: String) : LogItem()
        data class Entry(val log: CallLogEntity) : LogItem()
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is LogItem.Divider -> TYPE_DIVIDER
        is LogItem.Entry -> TYPE_ENTRY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DIVIDER -> DividerViewHolder(inflater.inflate(R.layout.item_call_log_divider, parent, false))
            else -> {
                if (timelineMode) {
                    TimelineLogViewHolder(ItemCallLogTimelineBinding.inflate(inflater, parent, false))
                } else {
                    LogViewHolder(ItemCallLogBinding.inflate(inflater, parent, false))
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when {
            holder is DividerViewHolder && item is LogItem.Divider -> holder.bind(item.date)
            holder is LogViewHolder && item is LogItem.Entry -> holder.bind(item.log)
            holder is TimelineLogViewHolder && item is LogItem.Entry -> holder.bind(item.log)
        }
    }

    fun setTimelineMode(enabled: Boolean) {
        if (this.timelineMode != enabled) {
            this.timelineMode = enabled
            notifyDataSetChanged()
        }
    }

    fun setSelection(ids: Set<Long>) {
        selectedIds = ids
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
        fun bind(date: String) { textView.text = date }
    }

    inner class LogViewHolder(private val binding: ItemCallLogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CallLogEntity) {
            bindCommon(
                binding.root, binding.avatarContainer, binding.callerAvatar,
                binding.callerName, binding.callerNumber, binding.tagLabel,
                binding.callTime, binding.callDirection, item
            )
            binding.quickCallButton.setOnClickListener { onQuickCall(item) }
            binding.quickMessageButton.setOnClickListener { onQuickMessage(item) }
        }
    }

    inner class TimelineLogViewHolder(private val binding: ItemCallLogTimelineBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: CallLogEntity) {
            bindCommon(
                binding.root, binding.avatarContainer, binding.callerAvatar,
                binding.callerName, binding.callerNumber, binding.tagLabel,
                binding.callTime, binding.callDirection, item
            )
        }
    }

    private fun bindCommon(
        rowRoot: View, avatarContainer: View, avatar: android.widget.ImageView,
        callerName: TextView, callerNumber: TextView, tagLabel: TextView,
        callTime: TextView, callDirection: android.widget.ImageView, item: CallLogEntity
    ) {
        val context = rowRoot.context
        val isSelected = selectedIds.contains(item.id)
        
        val fallbackName = if (item.phoneNumber.isBlank()) context.getString(R.string.unknown_caller) else item.phoneNumber
        callerName.text = item.displayName ?: fallbackName
        callerNumber.text = item.phoneNumber
        callTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))

        val tagText = CallLogTags.label(context, item.tag)
        tagLabel.text = tagText
        tagLabel.visibility = if (tagText.isNullOrBlank()) View.GONE else View.VISIBLE

        val isMissed = item.direction.lowercase(Locale.ROOT) == "missed"
        val iconRes = when (item.direction.lowercase(Locale.ROOT)) {
            "incoming" -> android.R.drawable.sym_call_incoming
            "outgoing" -> android.R.drawable.sym_call_outgoing
            "missed" -> android.R.drawable.sym_call_missed
            else -> android.R.drawable.sym_call_incoming
        }
        callDirection.setImageResource(iconRes)

        if (isMissed) {
            callerName.setTextColor(ContextCompat.getColor(context, R.color.call_red))
        } else {
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            callerName.setTextColor(typedValue.data)
        }

        rowRoot.alpha = if (isSelected) 0.6f else 1.0f
        rowRoot.setBackgroundResource(if (isSelected) R.color.bg_dark_elevated else android.R.color.transparent)

        val lookup = ContactLookup.lookup(context, item.phoneNumber)
        if (lookup.photo != null) {
            avatar.setImageBitmap(lookup.photo)
        } else {
            avatar.setImageResource(R.drawable.ic_launcher_foreground)
        }

        rowRoot.setOnClickListener { onEntryClick(item) }
        rowRoot.setOnLongClickListener { onEntryLongPress(item); true }
        avatarContainer.setOnClickListener { onContactClick(item) }
        tagLabel.setOnLongClickListener { onTagLongPress(item); true }
    }

    companion object {
        private const val TYPE_DIVIDER = 0
        private const val TYPE_ENTRY = 1
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LogItem>() {
            override fun areItemsTheSame(old: LogItem, new: LogItem): Boolean =
                if (old is LogItem.Entry && new is LogItem.Entry) old.log.id == new.log.id
                else if (old is LogItem.Divider && new is LogItem.Divider) old.date == new.date
                else false
            override fun areContentsTheSame(old: LogItem, new: LogItem): Boolean = old == new
        }
    }
}
