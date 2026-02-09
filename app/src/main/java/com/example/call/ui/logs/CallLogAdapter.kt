package com.example.call.ui.logs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.data.CallLogEntity
import com.example.call.databinding.ItemCallLogBinding
import com.example.call.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallLogAdapter(
    private val onCallClick: (CallLogEntity) -> Unit,
    private val onContactClick: (CallLogEntity) -> Unit
) : ListAdapter<CallLogEntity, CallLogAdapter.LogViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LogViewHolder(binding, onCallClick, onContactClick)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogViewHolder(
        private val binding: ItemCallLogBinding,
        private val onCallClick: (CallLogEntity) -> Unit,
        private val onContactClick: (CallLogEntity) -> Unit
    ) :
        RecyclerView.ViewHolder(binding.root) {
        
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

        fun bind(item: CallLogEntity) {
            val context = binding.root.context
            val fallbackName = if (item.phoneNumber.isBlank()) {
                context.getString(R.string.unknown_caller)
            } else {
                item.phoneNumber
            }
            
            binding.callerName.text = item.displayName ?: fallbackName
            binding.callerNumber.text = item.phoneNumber
            
            val date = Date(item.timestamp)
            val isToday = android.text.format.DateUtils.isToday(item.timestamp)
            binding.callTime.text = if (isToday) timeFormat.format(date) else dateFormat.format(date)

            val iconRes = when (item.direction.lowercase(Locale.ROOT)) {
                "incoming" -> android.R.drawable.sym_call_incoming
                "outgoing" -> android.R.drawable.sym_call_outgoing
                "missed" -> android.R.drawable.sym_call_missed
                else -> android.R.drawable.sym_call_incoming
            }
            binding.callDirection.setImageResource(iconRes)
            
            if (item.direction.lowercase(Locale.ROOT) == "missed") {
                binding.callerName.setTextColor(ContextCompat.getColor(context, R.color.call_red))
            } else {
                binding.callerName.setTextColor(ContextCompat.getColor(context, R.color.onSurface))
            }

            binding.root.setOnClickListener { onCallClick(item) }
            binding.root.setOnLongClickListener {
                onContactClick(item)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CallLogEntity>() {
            override fun areItemsTheSame(oldItem: CallLogEntity, newItem: CallLogEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: CallLogEntity, newItem: CallLogEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
