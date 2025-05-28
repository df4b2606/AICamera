// SelectedPromptViewModel.kt
package com.example.aicameranew.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.aicameranew.data.Prompt

class SelectedPromptViewModel : ViewModel() {
    private val _selectedPrompt = MutableLiveData<Prompt?>()
    val selectedPrompt: LiveData<Prompt?> get() = _selectedPrompt

    fun selectPrompt(prompt: Prompt) {
        _selectedPrompt.value = prompt
    }
}
