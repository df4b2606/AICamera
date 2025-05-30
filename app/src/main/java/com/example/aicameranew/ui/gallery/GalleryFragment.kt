package com.example.aicameranew.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.aicameranew.R
import com.example.aicameranew.data.Prompt
import com.example.aicameranew.data.PromptRepository
import com.example.aicameranew.databinding.FragmentGalleryBinding
import com.example.aicameranew.viewmodel.SelectedPromptViewModel

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private lateinit var promptRepository: PromptRepository
    private lateinit var promptAdapter: PromptAdapter
    private var currentPrompt: Prompt? = null
    private var isSortedByTime = true
    private val selectedPromptViewModel by activityViewModels<SelectedPromptViewModel>()

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promptRepository = PromptRepository(requireContext())

        val initialPrompts = promptRepository.getPromptsByTime()
        promptAdapter = PromptAdapter(
            initialPrompts,
            onItemClick = { clickedPrompt ->
                currentPrompt = clickedPrompt
                selectedPromptViewModel.selectPrompt(clickedPrompt)
                promptAdapter.notifyDataSetChanged()
                Toast.makeText(requireContext(), "Prompt Selected: ${clickedPrompt.text}", Toast.LENGTH_SHORT).show()
            },
            onDeleteClick = { promptToDelete ->
                promptRepository.deletePrompt(promptToDelete)
                val updatedList = if (isSortedByTime) promptRepository.getPromptsByTime() else promptRepository.getPromptsByFrequency()
                promptAdapter.updateData(updatedList)
            },
            selectedPromptProvider = { selectedPromptViewModel.selectedPrompt.value }
        )

        binding.promptList.adapter = promptAdapter
        binding.promptList.layoutManager = LinearLayoutManager(requireContext())

        binding.fab.setOnClickListener {
            findNavController().navigate(R.id.action_nav_gallery_to_nav_home)
        }

        binding.addPrompt.setOnClickListener {
            PromptDialogFragment { newText ->
                promptRepository.addPrompt(newText)
                val updatedList = if (isSortedByTime) promptRepository.getPromptsByTime() else promptRepository.getPromptsByFrequency()
                promptAdapter.updateData(updatedList)
            }.show(parentFragmentManager, "prompt_dialog")
        }

        binding.textSortInfo.setOnClickListener {
            isSortedByTime = !isSortedByTime
            val updatedList = if (isSortedByTime) promptRepository.getPromptsByTime() else promptRepository.getPromptsByFrequency()
            promptAdapter.updateData(updatedList)
            binding.textSortInfo.text = if (isSortedByTime) {
                "Sorted by: Time (Tap to change)"
            } else {
                "Sorted by: Frequency (Tap to change)"
            }
        }

        binding.checkboxGeolocation.isChecked = selectedPromptViewModel.isGeolocationEnabled.value ?: false
        binding.checkboxGeolocation.setOnCheckedChangeListener { _, isChecked ->
            selectedPromptViewModel.setGeolocationEnabled(isChecked)
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
                } else {
                    Toast.makeText(requireContext(), "Geolocation is ON.", Toast.LENGTH_SHORT).show()
                }
            } else {
                selectedPromptViewModel.setCurrentLocation(null)
                Toast.makeText(requireContext(), "Geolocation is OFF.", Toast.LENGTH_SHORT).show()
            }
        }

        selectedPromptViewModel.isGeolocationEnabled.observe(viewLifecycleOwner) { isEnabled ->
            if (binding.checkboxGeolocation.isChecked != isEnabled) {
                binding.checkboxGeolocation.isChecked = isEnabled
            }
        }

        binding.checkboxSensorAssistance.isChecked = selectedPromptViewModel.isSensorAssistanceEnabled.value ?: false
        binding.checkboxSensorAssistance.setOnCheckedChangeListener { _, isChecked ->
            selectedPromptViewModel.setSensorAssistanceEnabled(isChecked)
            Toast.makeText(requireContext(), if (isChecked) "Sensor Assistance is ON." else "Sensor Assistance is OFF.", Toast.LENGTH_SHORT).show()
        }

        selectedPromptViewModel.isSensorAssistanceEnabled.observe(viewLifecycleOwner) { isEnabled ->
            if (binding.checkboxSensorAssistance.isChecked != isEnabled) {
                binding.checkboxSensorAssistance.isChecked = isEnabled
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Location permission granted.", Toast.LENGTH_SHORT).show()
                binding.checkboxGeolocation.isChecked = true
                selectedPromptViewModel.setGeolocationEnabled(true)
            } else {
                Toast.makeText(requireContext(), "Location permission denied. Geolocation tagging will be disabled.", Toast.LENGTH_LONG).show()
                binding.checkboxGeolocation.isChecked = false
                selectedPromptViewModel.setGeolocationEnabled(false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
