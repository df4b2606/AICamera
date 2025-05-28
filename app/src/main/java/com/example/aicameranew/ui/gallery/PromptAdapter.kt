package com.example.aicameranew.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aicameranew.R
import com.example.aicameranew.data.Prompt

class PromptAdapter(
    private var prompts: List<Prompt>,
    private val onItemClick: (Prompt) -> Unit) :
    RecyclerView.Adapter<PromptAdapter.PromptViewHolder>() {

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val promptText: TextView = itemView.findViewById(R.id.text_prompt)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_card, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = prompts[position]
        holder.promptText.text = prompt.text
        holder.itemView.setOnClickListener {
            onItemClick(prompt)  // Use the callback
        }
    }

    override fun getItemCount(): Int = prompts.size

    fun updateData(newPrompts: List<Prompt>) {
        this.prompts = newPrompts
        notifyDataSetChanged()
    }
}
