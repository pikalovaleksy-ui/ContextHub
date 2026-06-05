package com.example.project.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.project.R
import com.example.project.databinding.FragmentMainBinding
import com.example.project.ui.rooms.RoomsFragment
import com.example.project.ui.social.SocialFragment
import com.example.project.ui.settings.SettingsFragment
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment : Fragment() {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by viewModels()

    private var currentTab = 0

    private val roomsFragment by lazy { RoomsFragment() }
    private val socialFragment by lazy { SocialFragment() }
    private val settingsFragment by lazy { SettingsFragment() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (childFragmentManager.findFragmentByTag("rooms") == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.tabContainer, roomsFragment, "rooms")
                .add(R.id.tabContainer, socialFragment, "social")
                .add(R.id.tabContainer, settingsFragment, "settings")
                .hide(socialFragment)
                .hide(settingsFragment)
                .commit()
        }
        showTab(currentTab)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_zones -> showTab(0)
                R.id.nav_social -> showTab(1)
                R.id.nav_settings -> showTab(2)
            }
            true
        }
    }

    private fun showTab(index: Int) {
        if (currentTab == index) return
        currentTab = index

        val fragments = listOf(roomsFragment, socialFragment, settingsFragment)
        val tx = childFragmentManager.beginTransaction()
        fragments.forEachIndexed { i, f ->
            if (i == index) tx.show(f) else tx.hide(f)
        }
        tx.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
