package com.example.call.ui.notes

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.call.data.AppDatabase
import com.example.call.data.NoteRepository
import com.example.call.databinding.ActivityNotesBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class NoteActivity : AppCompatActivity() {
    private lateinit var binding: ActivityNotesBinding
    private lateinit var repository: NoteRepository
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = AppDatabase.getInstance(this)
        repository = NoteRepository(db.noteDao())
        
        adapter = NoteAdapter(
            onPinToggle = { note ->
                lifecycleScope.launch {
                    repository.togglePin(note.id)
                }
            },
            onDelete = { note ->
                lifecycleScope.launch {
                    repository.deleteNote(note.id)
                }
            }
        )

        binding.notesList.layoutManager = LinearLayoutManager(this)
        binding.notesList.adapter = adapter

        binding.backButton.setOnClickListener { finish() }

        lifecycleScope.launch {
            repository.observeAll().collectLatest { notes ->
                adapter.submitList(notes)
            }
        }
    }
}
