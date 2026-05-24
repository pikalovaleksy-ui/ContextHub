package com.example.project.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.project.R
import com.example.project.databinding.FragmentDeviceScanBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DeviceScanFragment : Fragment() {

    private var _binding: FragmentDeviceScanBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDeviceScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retryButton.setOnClickListener {
            simulateScan()
        }

        binding.manualButton.setOnClickListener {
            findNavController().navigate(R.id.action_deviceScan_to_wifiSetup)
        }

        simulateScan()
    }

    private fun simulateScan() {
        binding.statusText.text = "Поиск устройств ESP32..."
        binding.scanProgress.visibility = View.VISIBLE
        binding.retryButton.visibility = View.GONE
        binding.devicesRecycler.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            if (isDetached) return@launch
            binding.scanProgress.visibility = View.GONE
            binding.retryButton.visibility = View.VISIBLE
            binding.statusText.text = "Найдено устройство: ContextHub-ESP32"
            binding.devicesRecycler.visibility = View.VISIBLE

            if (isAdded) {
                findNavController().navigate(R.id.action_deviceScan_to_wifiSetup)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
