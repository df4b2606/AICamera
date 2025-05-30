package com.example.aicameranew.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aicameranew.R
import com.example.aicameranew.data.Prompt
import java.text.SimpleDateFormat
import java.util.*

class PromptAdapter(
    private var prompts: List<Prompt>,
    private val onItemClick: (Prompt) -> Unit,
    private val onDeleteClick: (Prompt) -> Unit,
    private val selectedPromptProvider: () -> Prompt?  // ✅ 新增传入选中的 Prompt
) : RecyclerView.Adapter<PromptAdapter.PromptViewHolder>() {

    class PromptViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val promptText: TextView = itemView.findViewById(R.id.text_prompt)
        val timeText: TextView = itemView.findViewById(R.id.text_time)
        val btnDelete: ImageView = itemView.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PromptViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prompt_card, parent, false)
        return PromptViewHolder(view)
    }

    override fun onBindViewHolder(holder: PromptViewHolder, position: Int) {
        val prompt = prompts[position]
        holder.promptText.text = prompt.text

        // ✅ 时间格式化
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        holder.timeText.text = formatter.format(Date(prompt.timestamp))

        // ✅ 背景颜色判断
        val isSelected = prompt == selectedPromptProvider()
        holder.itemView.setBackgroundColor(
            if (isSelected)
                ContextCompat.getColor(holder.itemView.context, R.color.selected_prompt_bg)
            else
                ContextCompat.getColor(holder.itemView.context, android.R.color.white)
        )

        // ✅ 点击事件
        holder.itemView.setOnClickListener {
            onItemClick(prompt)
        }
        holder.btnDelete.setOnClickListener {
            onDeleteClick(prompt)
        }
    }

    override fun getItemCount(): Int = prompts.size

    fun updateData(newPrompts: List<Prompt>) {
        this.prompts = newPrompts
        notifyDataSetChanged()
    }
}
