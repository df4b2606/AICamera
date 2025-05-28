package com.example.aicameranew.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.aicameranew.R
import com.example.aicameranew.data.Prompt
import com.example.aicameranew.data.PromptRepository
import com.example.aicameranew.databinding.FragmentGalleryBinding
import com.example.aicameranew.viewmodel.SelectedPromptViewModel

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var promptRepository: PromptRepository
    private lateinit var promptAdapter: PromptAdapter
    private var currentPrompt: Prompt?=null
    private val selectedPromptViewModel by activityViewModels<SelectedPromptViewModel>()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val galleryViewModel =
            ViewModelProvider(this).get(GalleryViewModel::class.java)

        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textGallery
        galleryViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        promptRepository = PromptRepository(requireContext())
        //Initialize the RecyclerView
        //TODO: Change later time/frequency
        val initialPrompts=promptRepository.getPromptsByTime()
        promptAdapter = PromptAdapter(initialPrompts){
                clickedPrompt ->
            currentPrompt = clickedPrompt
            selectedPromptViewModel.selectPrompt(clickedPrompt)
            Toast.makeText(requireContext(), " Prompt Selected: ${clickedPrompt.text}", Toast.LENGTH_SHORT).show()
        }
        binding.promptList.adapter = promptAdapter
        binding.promptList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        binding.fab.setOnClickListener{
            findNavController().navigate(R.id.action_nav_gallery_to_nav_home)
        }

        binding.addPrompt.setOnClickListener {
            PromptDialogFragment {newText->
                promptRepository.addPrompt(newText)
                val updatedList = promptRepository.getPromptsByTime()
                promptAdapter.updateData(updatedList)
            }  //show the prompt input dialog
                .show(parentFragmentManager, "prompt_dialog")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}