package com.example.project.ui.zones

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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.project.R
import com.example.project.databinding.FragmentZonesBinding
import com.example.project.databinding.ItemZoneBinding
import com.example.project.model.Zone
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ZonesFragment : Fragment() {

    private var _binding: FragmentZonesBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ZonesViewModel by viewModels()

    private val adapter = ZoneAdapter(
        onToggle = { zone, enabled -> viewModel.toggleZoneEnabled(zone.id, enabled) },
        onDelete = { zone -> viewModel.deleteZone(zone.id) }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentZonesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.zonesRecycler.adapter = adapter

        binding.addZoneFab.setOnClickListener {
            findNavController().navigate(R.id.action_main_to_zoneEditor)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.zones.collect { zones ->
                    adapter.submitList(zones)
                    binding.emptyText.visibility = if (zones.isEmpty()) View.VISIBLE else View.GONE
                    binding.zonesRecycler.visibility = if (zones.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class ZoneAdapter(
    private val onToggle: (Zone, Boolean) -> Unit,
    private val onDelete: (Zone) -> Unit
) : ListAdapter<Zone, ZoneAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemZoneBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onToggle)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemZoneBinding,
        private val onToggle: (Zone, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(zone: Zone) {
            binding.zoneName.text = zone.name

            val count = zone.vertices.size
            binding.smartThingsAction.text = "$count вершин"

            binding.triggerBadge.visibility = View.GONE

            binding.enableSwitch.isChecked = zone.enabled
            binding.enableSwitch.setOnCheckedChangeListener(null)
            binding.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
                onToggle(zone, isChecked)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Zone>() {
        override fun areItemsTheSame(oldItem: Zone, newItem: Zone) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Zone, newItem: Zone) = oldItem == newItem
    }
}
