package com.example.call.ui.dialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.call.ui.contacts.ContactActivity

class ContactsContainerFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return View(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Navigate to full contacts activity
        startActivity(Intent(requireContext(), ContactActivity::class.java))
    }
}
