package com.example.project.ui.rooms

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.project.R
import com.example.project.databinding.FragmentRoomEditorBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RoomEditorFragment : Fragment() {

    private var _binding: FragmentRoomEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RoomEditorViewModel by viewModels()
    private var onBackPressedCallback: OnBackPressedCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun safeNavigateBack() {
        try {
            findNavController().popBackStack(com.example.project.R.id.mainFragment, false)
        } catch (e: Exception) {
            android.util.Log.e("RoomEditor", "Nav error", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var isProgrammaticChange = false

        val roomId = arguments?.getString("roomId") ?: return
        val roomName = arguments?.getString("roomName") ?: "Редактор"
        viewModel.init(roomId, roomName)

        onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { safeNavigateBack() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, onBackPressedCallback!!)

        with(binding) {
            mapView.isEditMode = true
            mapView.post { mapView.fitToView() }

            titleText.text = roomName
            backButton.setOnClickListener { safeNavigateBack() }

            zoneNameInput.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (isProgrammaticChange) return
                    viewModel.setNewZoneName(s?.toString() ?: "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            undoButton.setOnClickListener { viewModel.undoVertex() }
            clearButton.setOnClickListener { viewModel.clearVertices() }
            markPositionButton.setOnClickListener { viewModel.markCurrentPosition() }

            saveZoneButton.setOnClickListener { viewModel.saveNewZone() }
            deleteZoneButton.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Удалить зону?")
                    .setPositiveButton("Удалить") { _, _ -> viewModel.deleteSelectedZone() }
                    .setNegativeButton("Отмена", null)
                    .show()
            }

            mapView.onVertexAdded = { vertex -> viewModel.addVertexFromTap(vertex) }
            mapView.onZoneTapped = { zone -> viewModel.selectZone(zone?.id) }

            smartThingsButton.setOnClickListener {
                val zId = viewModel.uiState.value.selectedZoneId ?: return@setOnClickListener
                val args = Bundle().apply {
                    putString("zoneId", zId)
                    putString("roomId", roomId)
                }
                findNavController().navigate(R.id.action_roomEditor_to_smartThingsSetup, args)
            }
        }

        // Observe room zones separately for the map
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.roomZones.collect { allZones ->
                        val roomZones = allZones.filter { it.roomId == roomId }
                        binding.mapView.savedZones = roomZones
                    }
                }
            }
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.radarData.collect { data ->
                        binding.mapView.targets = data.targets
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.mapView.editingVertices = state.editingVertices
                        binding.mapView.editingZoneName = state.newZoneName
                        binding.mapView.editingZoneColor = state.newZoneColor
                        binding.mapView.statusText = state.statusText
                        binding.mapView.targetLabels = state.targetLabels
                        binding.mapView.selectedZoneId = state.selectedZoneId

                        binding.saveZoneButton.isEnabled = state.canSave
                        val cursorPos = binding.zoneNameInput.selectionStart
                        isProgrammaticChange = true
                        binding.zoneNameInput.setText(state.newZoneName)
                        binding.zoneNameInput.setSelection(minOf(cursorPos, state.newZoneName.length).coerceAtLeast(0))
                        isProgrammaticChange = false

                        binding.connectionStatus.setTextColor(
                            if (state.mqttConnected)
                                ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                            else
                                ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                        )

                        val hasSelected = state.selectedZoneId != null
                        binding.selectedZoneBar.visibility = if (hasSelected) View.VISIBLE else View.GONE
                        binding.zoneInputRow.visibility = if (hasSelected) View.GONE else View.VISIBLE
                        binding.actionButtonsRow.visibility = if (hasSelected) View.GONE else View.VISIBLE
                        binding.infoText.visibility = if (hasSelected) View.GONE else View.VISIBLE

                        if (hasSelected) {
                            val zone = viewModel.roomZones.value.find { it.id == state.selectedZoneId }
                            binding.selectedZoneLabel.text = zone?.name ?: "Зона выбрана"
                        }

                        when {
                            state.isSaving -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.saveZoneButton.isEnabled = false
                                binding.errorText.visibility = View.GONE
                            }
                            state.saveError != null -> {
                                binding.progressBar.visibility = View.GONE
                                binding.errorText.text = state.saveError
                                binding.errorText.visibility = View.VISIBLE
                            }
                            state.saveSuccess -> {
                                binding.progressBar.visibility = View.GONE
                                viewModel.selectZone(null)
                            }
                            else -> {
                                binding.progressBar.visibility = View.GONE
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        onBackPressedCallback?.remove()
        onBackPressedCallback = null
        super.onDestroyView()
        _binding = null
    }
}
