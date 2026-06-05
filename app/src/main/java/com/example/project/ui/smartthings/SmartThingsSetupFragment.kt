package com.example.project.ui.smartthings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.RadioGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.project.R
import com.example.project.databinding.FragmentSmartthingsSetupBinding
import com.example.project.databinding.ItemSmartthingsDeviceBinding
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SmartThingsSetupFragment : Fragment() {

    private var _binding: FragmentSmartthingsSetupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SmartThingsSetupViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSmartthingsSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val zoneId = arguments?.getString("zoneId") ?: return
        val roomId = arguments?.getString("roomId") ?: ""
        viewModel.init(zoneId, roomId)

        binding.backButton.setOnClickListener { findNavController().popBackStack() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is SmartThingsUiState.Loading -> {
                            binding.loadingBar.visibility = View.VISIBLE
                            binding.devicesRecycler.visibility = View.GONE
                            binding.errorText.visibility = View.GONE
                            binding.saveButton.visibility = View.GONE
                        }
                        is SmartThingsUiState.Ready -> {
                            binding.loadingBar.visibility = View.GONE
                            binding.errorText.visibility = View.GONE
                            binding.devicesRecycler.visibility = View.VISIBLE
                            binding.saveButton.visibility = View.VISIBLE
                            binding.titleText.text = "Устройства — ${state.zoneName}"
                            var adapter = binding.devicesRecycler.adapter as? DeviceAdapter
                            if (adapter == null) {
                                adapter = DeviceAdapter(
                                    onToggleDevice = { deviceId -> viewModel.toggleDevice(deviceId) },
                                    onActionChange = { deviceId, action -> viewModel.setAction(deviceId, action) }
                                )
                                binding.devicesRecycler.adapter = adapter
                            }
                            adapter.submitList(state.devices)
                        }
                        is SmartThingsUiState.Error -> {
                            binding.loadingBar.visibility = View.GONE
                            binding.devicesRecycler.visibility = View.GONE
                            binding.saveButton.visibility = View.GONE
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = state.message
                        }
                        is SmartThingsUiState.Saving -> {
                            binding.saveButton.isEnabled = false
                            binding.saveButton.text = "Сохранение..."
                        }
                        is SmartThingsUiState.Saved -> {
                            findNavController().popBackStack()
                        }
                    }
                }
            }
        }

        binding.saveButton.setOnClickListener { viewModel.save() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DeviceAdapter(
    private val onToggleDevice: (String) -> Unit,
    private val onActionChange: (String, String) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

    private var devices: List<DeviceBindingState> = emptyList()

    fun submitList(list: List<DeviceBindingState>) {
        devices = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = devices.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSmartthingsDeviceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onToggleDevice, onActionChange)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(devices[position])
    }

    class ViewHolder(
        private val binding: ItemSmartthingsDeviceBinding,
        private val onToggleDevice: (String) -> Unit,
        private val onActionChange: (String, String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentDeviceId: String = ""

        fun bind(device: DeviceBindingState) {
            currentDeviceId = device.deviceId
            binding.deviceName.text = device.deviceName
            binding.capabilitiesText.text = device.capabilities.joinToString(", ")
            binding.deviceCheckbox.isChecked = device.selected

            val hasSwitch = "switch" in device.capabilities
            binding.actionRow.visibility = if (device.selected && hasSwitch) View.VISIBLE else View.GONE

            if (device.action == "on") {
                binding.actionGroup.check(R.id.actionOn)
            } else {
                binding.actionGroup.check(R.id.actionOff)
            }

            binding.deviceCheckbox.setOnClickListener {
                onToggleDevice(currentDeviceId)
            }

            binding.root.setOnClickListener {
                binding.deviceCheckbox.performClick()
            }

            binding.actionGroup.setOnCheckedChangeListener { _, checkedId ->
                val action = if (checkedId == R.id.actionOn) "on" else "off"
                onActionChange(currentDeviceId, action)
            }
        }
    }
}