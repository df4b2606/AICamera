package com.example.aicameranew.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Prompt(val text: String, val timestamp: Long = System.currentTimeMillis(), var frequency: Int = 1)

class PromptRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("prompt_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()

    private val type = object : TypeToken<MutableList<Prompt>>() {}.type

    private var promptList: MutableList<Prompt> = loadPrompts().toMutableList()

    fun getPromptsByTime(): List<Prompt> = promptList.sortedByDescending { it.timestamp }

    fun getPromptsByFrequency(): List<Prompt> = promptList.sortedByDescending { it.frequency }

    private fun loadPrompts(): List<Prompt> {
        val json = prefs.getString("prompts_json", null) ?: return emptyList()
        return gson.fromJson(json, type) ?: emptyList()
    }

    private fun savePrompts() {
        val json = gson.toJson(promptList)
        prefs.edit().putString("prompts_json", json).apply()
    }

    fun addPrompt(text: String) {
        val newPrompt = Prompt(text)
        promptList.add(newPrompt)
        savePrompts()
    }

    fun deletePrompt(prompt: Prompt) {
        promptList.remove(prompt)
        savePrompts()
    }

    fun incrementFrequency(prompt: Prompt? = null) {
        val index = promptList.indexOfFirst { it.text == prompt!!.text }
        if (index != -1) {
            promptList[index].frequency += 1
            savePrompts()
        }
    }
}
