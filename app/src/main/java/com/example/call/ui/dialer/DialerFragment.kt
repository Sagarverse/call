package com.example.call.ui.dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.call.MainActivity
import com.example.call.databinding.FragmentDialerBinding
import com.example.call.util.ContactLookup
import java.util.Locale

class DialerFragment : Fragment() {
    private var _binding: FragmentDialerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DialerViewModel
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDialerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[DialerViewModel::class.java]

        setupDialPad()
        setupActions()
        setupSpeechRecognizer()
        
        binding.dialerInput.showSoftInputOnFocus = false
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext())
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Toast.makeText(requireContext(), "Listening...", Toast.LENGTH_SHORT).show()
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Toast.makeText(requireContext(), "Error recognizing speech", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        processVoiceCommand(matches[0])
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun processVoiceCommand(command: String) {
        val normalized = command.lowercase(Locale.getDefault())
        if (normalized.startsWith("call")) {
            val name = normalized.replace("call", "").trim()
            if (name.isNotEmpty()) {
                val number = ContactLookup.findPhoneNumberByName(requireContext(), name)
                if (number != null) {
                    viewModel.clearDigits()
                    number.forEach { viewModel.appendDigit(it.toString()) }
                    binding.dialerInput.setText(number)
                    (activity as? MainActivity)?.requestCallPermissionsIfNeeded()
                } else {
                    Toast.makeText(requireContext(), "Contact not found: $name", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        speechRecognizer?.startListening(intent)
    }

    private fun setupDialPad() {
        val dialButtons = listOf(
            Triple(binding.digit1, "1", ""),
            Triple(binding.digit2, "2", "ABC"),
            Triple(binding.digit3, "3", "DEF"),
            Triple(binding.digit4, "4", "GHI"),
            Triple(binding.digit5, "5", "JKL"),
            Triple(binding.digit6, "6", "MNO"),
            Triple(binding.digit7, "7", "PQRS"),
            Triple(binding.digit8, "8", "TUV"),
            Triple(binding.digit9, "9", "WXYZ"),
            Triple(binding.digitStar, "*", ""),
            Triple(binding.digit0, "0", "+"),
            Triple(binding.digitHash, "#", "")
        )

        dialButtons.forEach { (include, digit, letters) ->
            include.buttonNumber.text = digit
            include.buttonLetters.text = letters
            include.root.setOnClickListener {
                viewModel.appendDigit(digit)
                binding.dialerInput.setText(viewModel.dialedNumber.value)
            }
            include.root.setOnLongClickListener {
                if (digit == "0") {
                    viewModel.appendDigit("+")
                    binding.dialerInput.setText(viewModel.dialedNumber.value)
                    true
                } else false
            }
        }

        binding.backspace.setOnClickListener {
            viewModel.removeLastDigit()
            binding.dialerInput.setText(viewModel.dialedNumber.value)
        }

        binding.backspace.setOnLongClickListener {
            viewModel.clearDigits()
            binding.dialerInput.setText("")
            true
        }
    }

    private fun setupActions() {
        binding.callButton.setOnClickListener {
            (activity as? MainActivity)?.requestCallPermissionsIfNeeded()
        }
        
        binding.callButton.setOnLongClickListener {
            startListening()
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        speechRecognizer?.destroy()
        _binding = null
    }
}
