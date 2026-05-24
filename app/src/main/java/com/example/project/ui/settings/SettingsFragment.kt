package com.example.project.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.project.R
import com.example.project.data.remote.mqtt.MqttTopics
import com.example.project.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.user?.let { user ->
                        binding.nameInput.setText(user.name)
                        binding.emailInput.setText(user.email)
                    }
                    binding.brokerInput.setText(state.brokerUrl)
                    binding.serverInput.setText(state.serverUrl)

                    binding.mqttStatus.text = if (state.mqttConnected)
                        "Подключено" else "Отключено"
                    binding.mqttStatus.setTextColor(
                        if (state.mqttConnected)
                            resources.getColor(com.google.android.material.R.color.material_dynamic_primary50, null)
                        else
                            resources.getColor(android.R.color.darker_gray, null)
                    )
                    binding.mqttSwitch.isChecked = state.mqttConnected

                    if (state.isLoggedOut) {
                        findNavController().navigate(R.id.action_main_to_welcome)
                    }
                }
            }
        }

        binding.saveProfileButton.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim() ?: ""
            val email = binding.emailInput.text?.toString()?.trim() ?: ""
            viewModel.updateProfile(name, email)
            android.widget.Toast.makeText(requireContext(), "Профиль сохранён", android.widget.Toast.LENGTH_SHORT).show()
        }

        binding.mqttSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val broker = binding.brokerInput.text?.toString()?.trim() ?: MqttTopics.DEFAULT_BROKER_URL
                viewModel.connectMqtt(broker)
            } else {
                viewModel.disconnectMqtt()
            }
        }

        binding.brokerInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.saveBrokerUrl(binding.brokerInput.text?.toString()?.trim() ?: MqttTopics.DEFAULT_BROKER_URL)
            }
        }

        binding.serverInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.saveServerUrl(binding.serverInput.text?.toString()?.trim() ?: MqttTopics.DEFAULT_SERVER_URL)
            }
        }

        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Выход")
                .setMessage("Вы уверены, что хотите выйти?")
                .setPositiveButton("Выйти") { _, _ ->
                    viewModel.logout()
                }
                .setNegativeButton("Отмена", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
