package com.example.call.ui.dialer

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.call.ui.logs.CallLogActivity

class CallLogsContainerFragment : Fragment() {
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return View(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Navigate to full call logs activity
        startActivity(Intent(requireContext(), CallLogActivity::class.java))
    }
}
