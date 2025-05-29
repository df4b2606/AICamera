package com.example.aicameranew.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

//Prompt data class
data class Prompt(val text:String, val timestamp: Long=System.currentTimeMillis(),var frequency:Int=1)

class PromptRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("prompt_prefs", Context.MODE_PRIVATE)

    private val gson = Gson()
    private val type = object : TypeToken<MutableList<Prompt>>() {}.type

    private var promptList: MutableList<Prompt> = loadPrompts().toMutableList()

    //Sort the promptList
    fun getPromptsByTime(): List<Prompt> = promptList.sortedBy { it.timestamp }

    fun getPromptsByFrequency(): List<Prompt> = promptList.sortedBy { it.timestamp }

    //load the JSON string from SharedPreference

    private fun loadPrompts():List<Prompt>{
        val json=prefs.getString("prompts_json",null)?:return emptyList()
        return gson.fromJson(json,type)?:emptyList()
    }

    private fun savePrompts(){
        val json=gson.toJson(promptList)
        prefs.edit().putString("prompts_json",json).apply()
    }

    //Add and delete prompt

    fun addPrompt(text:String){
        val newPrompt=Prompt(text)
        promptList.add(newPrompt)
        savePrompts()
    }

    fun deletePrompt(prompt:Prompt){
        promptList.remove(prompt)
        savePrompts()
    }

}


//class PromptRepository(context: Context) {
//
//    private val prefs: SharedPreferences =
//        context.getSharedPreferences("prompt_prefs", Context.MODE_PRIVATE)
//
//    private val gson = Gson()
//    private val type = object : TypeToken<MutableList<Prompt>>() {}.type
//
//    private var promptList: MutableList<Prompt> = loadPrompts().toMutableList()
//
//    // 获取全部 prompt，按时间顺序
//    fun getPrompts(): List<Prompt> = promptList.sortedBy { it.timestamp }
//
//    // 添加新 prompt
//    fun addPrompt(text: String) {
//        val newPrompt = Prompt(text)
//        promptList.add(newPrompt)
//        savePrompts()
//    }
//
//    private fun savePrompts() {
//        val json = gson.toJson(promptList)
//        prefs.edit().putString("prompts_json", json).apply()
//    }
//
//    private fun loadPrompts(): List<Prompt> {
//        val json = prefs.getString("prompts_json", null) ?: return emptyList()
//        return gson.fromJson(json, type) ?: emptyList()
//    }
//}
