package com.example.project.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.project.R
import com.example.project.databinding.FragmentSocialBinding
import com.example.project.databinding.ItemFriendBinding
import com.example.project.model.Friend
import com.example.project.model.FriendStatus
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SocialFragment : Fragment() {

    private var _binding: FragmentSocialBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SocialViewModel by viewModels()

    private val adapter = FriendAdapter(
        onSendTouch = { friend -> viewModel.sendTouch(friend) },
        onRemove = { friend -> confirmRemove(friend) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSocialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.friendsRecycler.adapter = adapter

        binding.addFriendFab.setOnClickListener {
            findNavController().navigate(R.id.action_main_to_addFriend)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.friends.collect { friends ->
                    adapter.submitList(friends)
                    binding.emptyText.visibility =
                        if (friends.isEmpty()) View.VISIBLE else View.GONE
                    binding.friendsRecycler.visibility =
                        if (friends.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.touchReceived?.let { name ->
                        showTouchReceived(name)
                        viewModel.clearTouchReceived()
                    }
                }
            }
        }

        viewModel.loadMockFriends()
    }

    private fun showTouchReceived(name: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Social Touch")
            .setMessage("Получен Touch от $name!")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun confirmRemove(friend: Friend) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить друга")
            .setMessage("Удалить ${friend.name} из списка друзей?")
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.removeFriend(friend.id)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class FriendAdapter(
    private val onSendTouch: (Friend) -> Unit,
    private val onRemove: (Friend) -> Unit
) : ListAdapter<Friend, FriendAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onSendTouch)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemFriendBinding,
        private val onSendTouch: (Friend) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(friend: Friend) {
            binding.friendName.text = friend.name
            binding.statusText.text = if (friend.status == FriendStatus.ONLINE)
                "В сети" else "Не в сети"
            binding.statusDot.setBackgroundResource(
                if (friend.status == FriendStatus.ONLINE)
                    R.drawable.status_dot_online
                else
                    R.drawable.status_dot_offline
            )
            binding.sendTouchButton.setOnClickListener {
                onSendTouch(friend)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Friend>() {
        override fun areItemsTheSame(a: Friend, b: Friend) = a.id == b.id
        override fun areContentsTheSame(a: Friend, b: Friend) = a == b
    }
}
