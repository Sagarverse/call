package com.example.call.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.widget.Toast
import android.media.RingtoneManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.call.R
import com.example.call.databinding.ActivityContactsBinding
import com.example.call.util.ContactLookup
import com.example.call.util.ContactRingtoneStore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ContactActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactsBinding
    private val adapter = ContactAdapter(
        onCallClick = { contact -> startCall(contact.number) },
        onContactClick = { contact -> showContactOptions(contact) }
    )
    private var allContacts: List<ContactItem> = emptyList()
    private var pendingRingtoneNumber: String? = null

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val number = pendingRingtoneNumber ?: return@registerForActivityResult
        val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ContactRingtoneStore.setRingtoneUri(this, number, uri?.toString())
        pendingRingtoneNumber = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContacts()
        } else {
            Toast.makeText(this, getString(R.string.contacts_permission_needed), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.contactsList.layoutManager = LinearLayoutManager(this)
        binding.contactsList.adapter = adapter

        binding.backButton.setOnClickListener { finish() }

        binding.contactsSearchInput.addTextChangedListener { text ->
            val query = text?.toString().orEmpty()
            filterAndSubmit(query)
        }

        requestContactsIfNeeded()
    }

    private fun requestContactsIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            loadContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                val results = ArrayList<ContactItem>()
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    sort
                )?.use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )
                    val numberIndex = cursor.getColumnIndexOrThrow(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    val seen = HashSet<String>()
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex).orEmpty()
                        val number = cursor.getString(numberIndex).orEmpty()
                        if (number.isBlank() || seen.contains(number)) continue
                        seen.add(number)
                        results.add(ContactItem(name, number))
                    }
                }
                results
            }
            allContacts = contacts
            submitSectioned(contacts)
        }
    }

    private fun filterAndSubmit(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            submitSectioned(allContacts)
            return
        }
        val lower = trimmed.lowercase(Locale.getDefault())
        val filtered = allContacts.filter { contact ->
            contact.name.lowercase(Locale.getDefault()).contains(lower) ||
                contact.number.contains(lower)
        }
        submitSectioned(filtered)
    }

    private fun submitSectioned(contacts: List<ContactItem>) {
        val rows = ArrayList<ContactAdapter.ContactRow>()
        var lastHeader: String? = null
        contacts.forEach { contact ->
            val header = sectionKey(contact)
            if (header != lastHeader) {
                rows.add(ContactAdapter.ContactRow.Header(header))
                lastHeader = header
            }
            rows.add(ContactAdapter.ContactRow.Entry(contact))
        }
        adapter.submitList(rows)
    }

    private fun sectionKey(contact: ContactItem): String {
        val source = if (contact.name.isNotBlank()) contact.name else contact.number
        val first = source.trim().firstOrNull() ?: '#'
        val upper = first.uppercaseChar()
        return if (upper in 'A'..'Z') upper.toString() else "#"
    }

    private fun startCall(number: String) {
        if (number.isBlank()) return
        val uri = Uri.fromParts("tel", number, null)
        val telecomManager = getSystemService(TelecomManager::class.java)
        try {
            telecomManager.placeCall(uri, Bundle())
        } catch (_: SecurityException) {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    private fun openOrCreateContact(number: String) {
        if (number.isBlank()) return
        val contactUri = ContactLookup.findContactUri(this, number)
        if (contactUri != null) {
            startActivity(Intent(Intent.ACTION_VIEW, contactUri))
        } else {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        }
    }

    private fun showContactOptions(contact: ContactItem) {
        val options = arrayOf(
            getString(R.string.open_contact),
            getString(R.string.set_ringtone),
            getString(R.string.clear_ringtone)
        )

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.contact_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openOrCreateContact(contact.number)
                    1 -> launchRingtonePicker(contact.number)
                    2 -> ContactRingtoneStore.setRingtoneUri(this, contact.number, null)
                }
            }
            .show()
    }

    private fun launchRingtonePicker(number: String) {
        pendingRingtoneNumber = number
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            val existing = ContactRingtoneStore.getRingtoneUri(this@ContactActivity, number)
            if (!existing.isNullOrBlank()) {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }
}

