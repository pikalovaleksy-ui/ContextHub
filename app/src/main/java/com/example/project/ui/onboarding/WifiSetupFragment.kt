package com.example.project.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.project.R
import com.example.project.databinding.FragmentWifiSetupBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WifiSetupFragment : Fragment() {

    private var _binding: FragmentWifiSetupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWifiSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.connectButton.setOnClickListener {
            val ssid = binding.ssidInput.text?.toString()?.trim() ?: ""
            val password = binding.passwordInput.text?.toString() ?: ""

            if (ssid.isBlank()) {
                binding.errorText.text = "Введите название Wi-Fi сети"
                binding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            binding.errorText.visibility = View.GONE
            findNavController().navigate(R.id.action_wifiSetup_to_connection)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
