package com.example.call.ui.dialer

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.databinding.ItemContactSuggestionBinding

data class ContactSuggestion(
    val name: String,
    val number: String
)

class ContactSuggestionAdapter(
    private val onCallClick: (ContactSuggestion) -> Unit
) : ListAdapter<ContactSuggestion, ContactSuggestionAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactSuggestionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onCallClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemContactSuggestionBinding,
        private val onCallClick: (ContactSuggestion) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactSuggestion) {
            val displayName = if (item.name.isBlank()) item.number else item.name
            binding.suggestionName.text = displayName
            binding.suggestionNumber.text = item.number
            binding.root.setOnClickListener { onCallClick(item) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ContactSuggestion>() {
            override fun areItemsTheSame(
                oldItem: ContactSuggestion,
                newItem: ContactSuggestion
            ): Boolean = oldItem.number == newItem.number

            override fun areContentsTheSame(
                oldItem: ContactSuggestion,
                newItem: ContactSuggestion
            ): Boolean = oldItem == newItem
        }
    }
}
