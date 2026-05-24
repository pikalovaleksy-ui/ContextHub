package com.example.project.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.project.databinding.FragmentAddFriendBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AddFriendFragment : Fragment() {

    private var _binding: FragmentAddFriendBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SocialViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddFriendBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.scanQrCard.setOnClickListener {
            binding.nameInput.setText("Новый друг (QR)")
            binding.deviceIdInput.setText("device_qr_" + System.currentTimeMillis().toString().takeLast(6))
        }

        binding.myQrCard.setOnClickListener {
            val myId = viewModel.getOwnDeviceId()
            android.widget.Toast.makeText(
                requireContext(),
                "Ваш ID: $myId (QR-код будет сгенерирован)",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        binding.addButton.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim() ?: ""
            val deviceId = binding.deviceIdInput.text?.toString()?.trim() ?: ""

            if (name.isBlank() || deviceId.isBlank()) {
                binding.errorText.text = "Заполните имя и ID устройства"
                binding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            viewModel.addFriend(name, deviceId)
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
