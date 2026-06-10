package com.example.project.ui.social

import android.app.AlertDialog
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.project.R
import com.example.project.databinding.FragmentAddFriendBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
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
            findNavController().navigate(R.id.action_addFriend_to_scanQr)
        }

        binding.myQrCard.setOnClickListener {
            showMyQrDialog()
        }

        binding.addButton.setOnClickListener {
            val text = binding.deviceIdInput.text?.toString()?.trim() ?: ""
            val name = binding.nameInput.text?.toString()?.trim() ?: ""

            if (text.isBlank() || name.isBlank()) {
                binding.errorText.text = getString(R.string.social_fill_fields)
                binding.errorText.visibility = View.VISIBLE
                return@setOnClickListener
            }

            viewModel.lookUpAndAddFriend(text, name) { success ->
                if (success) {
                    findNavController().navigateUp()
                } else {
                    binding.errorText.text = getString(R.string.social_user_not_found)
                    binding.errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun showMyQrDialog() {
        val myId = viewModel.getOwnDeviceId()
        val qrContent = "contexthub://add-friend?deviceId=$myId"

        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrContent, BarcodeFormat.QR_CODE, 512, 512)
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565)
        for (x in 0 until 512) {
            for (y in 0 until 512) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) 0xFF1D192B.toInt() else 0xFFFFFFFF.toInt())
            }
        }

        val imageView = ImageView(requireContext()).apply {
            setImageBitmap(bitmap)
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.social_my_qr_title))
            .setMessage("ID: $myId")
            .setView(imageView)
            .setPositiveButton(getString(android.R.string.ok), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
