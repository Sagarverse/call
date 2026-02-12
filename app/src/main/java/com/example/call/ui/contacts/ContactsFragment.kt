package com.example.call.ui.contacts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.call.MainActivity
import com.example.call.R
import com.example.call.databinding.ActivityContactsBinding
import com.example.call.util.BlockedNumberStore
import com.example.call.util.ContactLookup
import com.example.call.util.ContactRingtoneStore
import com.example.call.util.FavoritesStore
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ContactsFragment : Fragment() {
    private var _binding: ActivityContactsBinding? = null
    private val binding get() = _binding!!
    private val adapter = ContactAdapter(
        onCallClick = { contact -> startCall(contact.number) },
        onContactClick = { contact -> showContactOptions(contact) },
        onQuickMessage = { contact -> startMessage(contact.number) }
    )
    private var allContacts: List<ContactItem> = emptyList()
    private var pendingRingtoneNumber: String? = null

    private val ringtonePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
        val number = pendingRingtoneNumber ?: return@registerForActivityResult
        val uri: Uri? = result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        ContactRingtoneStore.setRingtoneUri(requireContext(), number, uri?.toString())
        pendingRingtoneNumber = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadContacts()
        } else {
            Toast.makeText(requireContext(), getString(R.string.contacts_permission_needed), Toast.LENGTH_LONG)
                .show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ActivityContactsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.contactsList.layoutManager = LinearLayoutManager(requireContext())
        binding.contactsList.adapter = adapter
        binding.contactsList.setHasFixedSize(true)
        binding.contactsList.itemAnimator = null
        binding.contactsList.setItemViewCacheSize(16)

        binding.backButton.setOnClickListener {
            (activity as? MainActivity)?.showDialerPage()
        }

        binding.contactsSearchInput.addTextChangedListener { text ->
            val query = text?.toString().orEmpty()
            filterAndSubmit(query)
        }

        requestContactsIfNeeded()
    }

    private fun requestContactsIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            loadContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun loadContacts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                val results = ArrayList<ContactItem>()
                val projection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                val sort = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
                requireContext().contentResolver.query(
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
        val telecomManager = requireContext().getSystemService(TelecomManager::class.java)
        try {
            telecomManager.placeCall(uri, Bundle())
        } catch (_: SecurityException) {
            startActivity(Intent(Intent.ACTION_DIAL, uri))
        }
    }

    private fun openOrCreateContact(number: String) {
        if (number.isBlank()) return
        val contactUri = ContactLookup.findContactUri(requireContext(), number)
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
        val actions = mutableListOf<String>()
        val handlers = mutableListOf<() -> Unit>()
        val isBlocked = BlockedNumberStore.isBlocked(requireContext(), contact.number)
        val isFavorite = FavoritesStore.getFavorites(requireContext()).any { it.number == contact.number }

        actions.add(getString(R.string.open_contact))
        handlers.add { openOrCreateContact(contact.number) }

        actions.add(getString(R.string.set_ringtone))
        handlers.add { launchRingtonePicker(contact.number) }

        actions.add(if (isFavorite) getString(R.string.remove_favorite) else getString(R.string.add_favorite))
        handlers.add { toggleFavorite(contact, isFavorite) }

        actions.add(if (isBlocked) getString(R.string.unblock_number) else getString(R.string.block_number))
        handlers.add { toggleBlock(contact.number, isBlocked) }

        actions.add(getString(R.string.copy_number))
        handlers.add { copyNumber(contact.number) }

        showActionSheet(getString(R.string.contact_options), actions, handlers)
    }

    private fun launchRingtonePicker(number: String) {
        pendingRingtoneNumber = number
        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            val existing = ContactRingtoneStore.getRingtoneUri(requireContext(), number)
            if (!existing.isNullOrBlank()) {
                putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    private fun startMessage(number: String) {
        if (number.isBlank()) return
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
    }

    private fun startEmail(number: String) {
        val email = ContactLookup.findContactEmail(requireContext(), number)
        if (email.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.no_email_found), Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
    }

    private fun editContact(number: String) {
        val contactUri = ContactLookup.findContactUri(requireContext(), number)
        if (contactUri == null) {
            Toast.makeText(requireContext(), getString(R.string.contact_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_EDIT, contactUri).apply {
            putExtra("finishActivityOnSaveCompleted", true)
        }
        startActivity(intent)
    }

    private fun deleteContact(number: String) {
        val contactUri = ContactLookup.findContactUri(requireContext(), number)
        if (contactUri == null) {
            Toast.makeText(requireContext(), getString(R.string.contact_not_found), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_DELETE, contactUri)
        startActivity(intent)
    }

    private fun shareContact(number: String) {
        val contactUri = ContactLookup.findContactUri(requireContext(), number)
        if (contactUri != null) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = ContactsContract.Contacts.CONTENT_VCARD_TYPE
                putExtra(Intent.EXTRA_STREAM, contactUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_contact)))
        } else {
            shareNumber(number)
        }
    }

    private fun shareNumber(number: String) {
        if (number.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, number)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_number)))
    }

    private fun copyNumber(number: String) {
        if (number.isBlank()) return
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.phone_number), number))
        Toast.makeText(requireContext(), getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private fun toggleFavorite(contact: ContactItem, isFavorite: Boolean) {
        if (isFavorite) {
            FavoritesStore.removeFavorite(requireContext(), contact.number)
            Toast.makeText(requireContext(), getString(R.string.favorite_removed), Toast.LENGTH_SHORT).show()
        } else {
            FavoritesStore.addFavorite(requireContext(), FavoritesStore.Favorite(contact.name, contact.number))
            Toast.makeText(requireContext(), getString(R.string.favorite_added), Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleBlock(number: String, isBlocked: Boolean) {
        if (isBlocked) {
            BlockedNumberStore.unblock(requireContext(), number)
            Toast.makeText(requireContext(), getString(R.string.number_unblocked), Toast.LENGTH_SHORT).show()
        } else {
            BlockedNumberStore.block(requireContext(), number)
            Toast.makeText(requireContext(), getString(R.string.number_blocked), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showActionSheet(
        title: String,
        actions: List<String>,
        handlers: List<() -> Unit>
    ) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_action_sheet, null)
        val titleView = view.findViewById<android.widget.TextView>(R.id.sheetTitle)
        val listView = view.findViewById<android.widget.ListView>(R.id.sheetList)
        titleView.text = title
        listView.adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, actions)
        listView.setOnItemClickListener { _, _, position, _ ->
            dialog.dismiss()
            handlers.getOrNull(position)?.invoke()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
