package com.example.call.ui.notes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.call.data.NoteEntity
import com.example.call.databinding.ItemNoteBinding
import java.text.DateFormat
import java.util.Date

class NoteAdapter(private val onDelete: (NoteEntity) -> Unit) :
    ListAdapter<NoteEntity, NoteAdapter.NoteViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return NoteViewHolder(binding, onDelete)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class NoteViewHolder(
        private val binding: ItemNoteBinding,
        private val onDelete: (NoteEntity) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NoteEntity) {
            binding.noteText.text = item.note
            val dateStr = DateFormat.getDateTimeInstance().format(Date(item.timestamp))
            val callerInfo = item.displayName ?: item.phoneNumber
            binding.noteMetadata.text = "$callerInfo • $dateStr"
            binding.noteTag.text = item.tag ?: ""
            
            binding.root.setOnLongClickListener {
                onDelete(item)
                true
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<NoteEntity>() {
            override fun areItemsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: NoteEntity, newItem: NoteEntity): Boolean {
                return oldItem == newItem
            }
        }
    }
}
