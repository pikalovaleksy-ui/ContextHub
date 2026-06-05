package com.example.project.ui.rooms

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
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
import com.example.project.databinding.ItemRoomBinding
import com.example.project.databinding.FragmentRoomsBinding
import com.example.project.model.Target
import com.example.project.model.Zone
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RoomsFragment : Fragment() {

    private var _binding: FragmentRoomsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: RoomsViewModel by viewModels()

    private val adapter = RoomAdapter(
        onEdit = { roomWithZones ->
            val args = Bundle().apply {
                putString("roomId", roomWithZones.room.id)
                putString("roomName", roomWithZones.room.name)
            }
            findNavController().navigate(R.id.action_main_to_roomEditor, args)
        },
        onDeleteRoom = { roomWithZones ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Удалить комнату?")
                .setMessage("Все зоны в комнате будут удалены")
                .setPositiveButton("Удалить") { _, _ ->
                    viewModel.deleteRoom(roomWithZones.room.id)
                }
                .setNegativeButton("Отмена", null)
                .show()
        },
        onToggle = { zone, enabled ->
            viewModel.toggleZoneEnabled(zone.id, enabled)
        },
        onDeleteZone = { zone ->
            viewModel.deleteZone(zone.id)
        }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRoomsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.roomsRecycler.adapter = adapter

        binding.addRoomFab.setOnClickListener {
            showCreateRoomDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.radarTargets.collect { targets ->
                        adapter.updateTargets(targets)
                    }
                }
                launch {
                    viewModel.roomsWithZones.collect { rooms ->
                        adapter.submitList(rooms)
                        val isEmpty = rooms.isEmpty()
                        binding.emptyText.visibility = if (isEmpty) View.VISIBLE else View.GONE
                        binding.roomsRecycler.visibility = if (isEmpty) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    private fun showCreateRoomDialog() {
        val input = android.widget.EditText(requireContext()).apply {
            hint = "Название комнаты"
            setTextColor(resources.getColor(android.R.color.black, null))
            setHintTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Новая комната")
            .setView(input)
            .setPositiveButton("Создать") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    viewModel.createRoom(name)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class RoomAdapter(
    private val onEdit: (RoomWithZones) -> Unit,
    private val onDeleteRoom: (RoomWithZones) -> Unit,
    private val onToggle: (Zone, Boolean) -> Unit,
    private val onDeleteZone: (Zone) -> Unit
) : ListAdapter<RoomWithZones, RoomAdapter.ViewHolder>(DiffCallback()) {

    private var currentTargets: List<Target> = emptyList()
    private var attachedRecyclerView: RecyclerView? = null

    fun updateTargets(targets: List<Target>) {
        currentTargets = targets
        attachedRecyclerView?.let { rv ->
            for (i in 0 until rv.childCount) {
                val holder = rv.getChildViewHolder(rv.getChildAt(i)) as? ViewHolder
                holder?.updateMiniMapTargets(targets)
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRoomBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding, onEdit, onDeleteRoom, onToggle, onDeleteZone)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), currentTargets)
    }

    class ViewHolder(
        private val binding: ItemRoomBinding,
        private val onEdit: (RoomWithZones) -> Unit,
        private val onDeleteRoom: (RoomWithZones) -> Unit,
        private val onToggle: (Zone, Boolean) -> Unit,
        private val onDeleteZone: (Zone) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var currentItem: RoomWithZones? = null
        private val ctx = binding.root.context

        fun bind(item: RoomWithZones, targets: List<Target>) {
            currentItem = item
            binding.roomName.text = item.room.name

            // Mini map
            binding.miniMap.isInteractive = true
            binding.miniMap.showGridCoordinates = false
            binding.miniMap.savedZones = item.zones
            binding.miniMap.targets = targets
            binding.miniMap.post { binding.miniMap.fitToView() }

            // Zone toggles - wrapping layout
            val zonesContainer = binding.zonesContainer
            zonesContainer.removeAllViews()

            if (item.zones.isEmpty()) {
                binding.zoneCountText.text = "Нет зон"
                binding.zoneCountText.visibility = View.VISIBLE
            } else {
                binding.zoneCountText.visibility = View.GONE
                // Schedule wrapping after layout
                zonesContainer.post {
                    buildWrappedZoneToggles(zonesContainer, item.zones)
                }
            }

            binding.editRoomButton.setOnClickListener {
                currentItem?.let { onEdit(it) }
            }
            binding.deleteRoomButton.setOnClickListener {
                currentItem?.let { onDeleteRoom(it) }
            }
        }

        fun updateMiniMapTargets(targets: List<Target>) {
            binding.miniMap.targets = targets
        }

        private fun buildWrappedZoneToggles(container: ViewGroup, zones: List<Zone>) {
            container.removeAllViews()
            val availableWidth = container.width - container.paddingLeft - container.paddingRight
            if (availableWidth <= 0) return

            val borderColor = 0xFFD0BCFF.toInt()
            val marginDp = (8 * ctx.resources.displayMetrics.density).toInt()
            val avgCharWidth = ctx.resources.displayMetrics.density * 10
            val switchExtra = 120 * ctx.resources.displayMetrics.density

            val rows = mutableListOf<MutableList<Zone>>()
            var currentRow = mutableListOf<Zone>()
            var currentRowWidth = 0f

            for (zone in zones) {
                val zoneWidth = zone.name.length * avgCharWidth + switchExtra
                if (zoneWidth > availableWidth * 0.9f) {
                    if (currentRow.isNotEmpty()) {
                        rows.add(currentRow); currentRow = mutableListOf(); currentRowWidth = 0f
                    }
                    rows.add(mutableListOf(zone))
                } else if (currentRowWidth + zoneWidth > availableWidth && currentRow.isNotEmpty()) {
                    rows.add(currentRow); currentRow = mutableListOf(zone); currentRowWidth = zoneWidth
                } else {
                    currentRow.add(zone); currentRowWidth += zoneWidth
                }
            }
            if (currentRow.isNotEmpty()) rows.add(currentRow)

            for (rowZones in rows) {
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                }
                for (zone in rowZones) {
                    val bgColor = if (zone.color != 0) zone.color else borderColor
                    val switch = SwitchMaterial(ctx).apply {
                        text = zone.name
                        isChecked = zone.enabled
                        setOnCheckedChangeListener(null)
                        setOnCheckedChangeListener { _, isChecked -> onToggle(zone, isChecked) }
                        layoutParams = android.view.ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                        val pad = (4 * ctx.resources.displayMetrics.density).toInt()
                        setPadding(pad, 0, pad, 0)
                    }
                    val card = android.widget.FrameLayout(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                            setMargins(marginDp / 4, marginDp / 4, marginDp / 4, marginDp / 4)
                        }
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.argb(
                                25,
                                android.graphics.Color.red(bgColor),
                                android.graphics.Color.green(bgColor),
                                android.graphics.Color.blue(bgColor)
                            ))
                            cornerRadius = 12f * ctx.resources.displayMetrics.density
                        }
                        addView(switch)
                    }
                    row.addView(card)
                }
                container.addView(row)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<RoomWithZones>() {
        override fun areItemsTheSame(oldItem: RoomWithZones, newItem: RoomWithZones) =
            oldItem.room.id == newItem.room.id
        override fun areContentsTheSame(oldItem: RoomWithZones, newItem: RoomWithZones) =
            oldItem == newItem
    }
}
