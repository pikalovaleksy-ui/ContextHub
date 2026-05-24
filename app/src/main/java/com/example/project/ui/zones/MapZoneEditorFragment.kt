package com.example.project.ui.zones

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.project.databinding.FragmentMapZoneEditorBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MapZoneEditorFragment : Fragment() {

    private var _binding: FragmentMapZoneEditorBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MapZoneEditorViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapZoneEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun safeNavigateBack() {
        viewModel.disconnectFromRadar()
        try {
            val navController = findNavController()
            if (!navController.popBackStack()) {
                // Back stack empty — идём напрямую на mainFragment
                navController.navigate(com.example.project.R.id.mainFragment) {
                    popUpTo(0) { inclusive = true }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ZoneEditor", "Nav error", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        var isProgrammaticChange = false

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    safeNavigateBack()
                }
            }
        )

        with(binding) {
            mapView.isEditMode = true
            mapView.savedZones = emptyList()
            mapView.post { mapView.fitToView() }

            // Connect
            viewModel.connectToRadar()

            // Back
            backButton.setOnClickListener { safeNavigateBack() }

            // Zone name input
            zoneNameInput.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    if (isProgrammaticChange) return
                    viewModel.setZoneName(s?.toString() ?: "")
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })

            // Undo
            undoButton.setOnClickListener {
                viewModel.undoVertex()
            }

            // Clear
            clearButton.setOnClickListener {
                viewModel.clearVertices()
            }

            // Mark Position - use nearest target's position
            markPositionButton.setOnClickListener {
                viewModel.markCurrentPosition()
            }

            // Save zone
            saveZoneButton.setOnClickListener {
                viewModel.saveZone()
            }

            // Map tap for adding vertex
            mapView.onVertexAdded = { vertex ->
                viewModel.addVertexFromTap(vertex)
            }
        }

        // Observe state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.radarData.collect { data ->
                        android.util.Log.d("RadarDebug", "Targets arrived: ${data.targets.size}")
                        data.targets.forEach { t ->
                            android.util.Log.d("RadarDebug", "  target #${t.id}: (${t.x}, ${t.y}) speed=${t.speed}")
                        }
                        binding.mapView.targets = data.targets
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        binding.mapView.editingVertices = state.vertices
                        binding.mapView.editingZoneName = state.zoneName
                        binding.mapView.statusText = state.statusText
                        binding.mapView.targetLabels = state.targetLabels

                        binding.saveZoneButton.isEnabled = state.canSave
                        isProgrammaticChange = true
                        binding.zoneNameInput.setText(state.zoneName)
                        isProgrammaticChange = false

                        binding.connectionStatus.setTextColor(
                            if (state.mqttConnected)
                                resources.getColor(android.R.color.holo_green_light, null)
                            else
                                resources.getColor(android.R.color.holo_red_light, null)
                        )

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
                                safeNavigateBack()
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
        super.onDestroyView()
        _binding = null
    }
}
