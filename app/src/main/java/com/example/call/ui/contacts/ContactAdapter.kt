package com.example.call.ui.contacts

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.databinding.ItemContactBinding
import com.example.call.databinding.ItemContactHeaderBinding

class ContactAdapter(
    private val onCallClick: (ContactItem) -> Unit,
    private val onContactClick: (ContactItem) -> Unit
) : ListAdapter<ContactAdapter.ContactRow, RecyclerView.ViewHolder>(DIFF) {

    sealed class ContactRow {
        data class Header(val title: String) : ContactRow()
        data class Entry(val contact: ContactItem) : ContactRow()
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ContactRow.Header -> TYPE_HEADER
            is ContactRow.Entry -> TYPE_ENTRY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val headerBinding = ItemContactHeaderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            HeaderViewHolder(headerBinding)
        } else {
            val binding = ItemContactBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            ContactViewHolder(binding, onCallClick, onContactClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ContactRow.Header -> (holder as HeaderViewHolder).bind(item)
            is ContactRow.Entry -> (holder as ContactViewHolder).bind(item)
        }
    }

    class ContactViewHolder(
        private val binding: ItemContactBinding,
        private val onCallClick: (ContactItem) -> Unit,
        private val onContactClick: (ContactItem) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactRow.Entry) {
            val contact = item.contact
            val displayName = if (contact.name.isBlank()) contact.number else contact.name
            binding.contactName.text = displayName
            binding.contactNumber.text = contact.number
            binding.root.setOnClickListener { onCallClick(contact) }
            binding.root.setOnLongClickListener {
                onContactClick(contact)
                true
            }
        }
    }

    class HeaderViewHolder(
        private val binding: ItemContactHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ContactRow.Header) {
            binding.contactHeader.text = item.title
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1

        private val DIFF = object : DiffUtil.ItemCallback<ContactRow>() {
            override fun areItemsTheSame(oldItem: ContactRow, newItem: ContactRow): Boolean {
                return when {
                    oldItem is ContactRow.Header && newItem is ContactRow.Header ->
                        oldItem.title == newItem.title
                    oldItem is ContactRow.Entry && newItem is ContactRow.Entry ->
                        oldItem.contact.number == newItem.contact.number
                    else -> false
                }
            }

            override fun areContentsTheSame(oldItem: ContactRow, newItem: ContactRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
