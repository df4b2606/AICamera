package com.example.aicameranew.viewmodel

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.aicameranew.data.Prompt

class SelectedPromptViewModel : ViewModel() {

    private val _selectedPrompt = MutableLiveData<Prompt?>()
    val selectedPrompt: LiveData<Prompt?> = _selectedPrompt

    // LiveData for geolocation preference
    private val _isGeolocationEnabled = MutableLiveData<Boolean>().apply { value = false }
    val isGeolocationEnabled: LiveData<Boolean> = _isGeolocationEnabled

    // LiveData for storing the current location
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation

    // NEW: LiveData for sensor assistance preference
    private val _isSensorAssistanceEnabled = MutableLiveData<Boolean>().apply { value = false }
    val isSensorAssistanceEnabled: LiveData<Boolean> = _isSensorAssistanceEnabled

    fun selectPrompt(prompt: Prompt) {
        _selectedPrompt.value = prompt
    }

    fun setGeolocationEnabled(enabled: Boolean) {
        _isGeolocationEnabled.value = enabled
    }

    fun setCurrentLocation(location: Location?) {
        _currentLocation.value = location
    }

    // NEW: Function to set sensor assistance preference
    fun setSensorAssistanceEnabled(enabled: Boolean) {
        _isSensorAssistanceEnabled.value = enabled
    }
}