package com.example.project.ui.onboarding

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
import com.example.project.databinding.FragmentConnectionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.retryButton.setOnClickListener {
            viewModel.connect()
        }

        binding.continueButton.setOnClickListener {
            findNavController().navigate(R.id.action_connection_to_main)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500)
            viewModel.connect()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.connectionProgress.visibility =
                        if (state.isConnecting) View.VISIBLE else View.GONE
                    binding.retryButton.visibility =
                        if (state.isError) View.VISIBLE else View.GONE

                    if (state.isConnected) {
                        binding.statusText.text = getString(R.string.connection_success)
                        binding.continueButton.visibility = View.VISIBLE
                    } else if (state.isError) {
                        binding.statusText.text = getString(R.string.connection_error)
                    } else {
                        binding.statusText.text = getString(R.string.connection_status)
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
