package com.example.aicameranew.ui.gallery

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.aicameranew.R

class PromptDialogFragment(
    private val onPromptSaved: (String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_promptdialog, null)
        val editText = view.findViewById<EditText>(R.id.edit_prompt)
        val saveButton = view.findViewById<Button>(R.id.btn_save)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(view)
            .create()

        saveButton.setOnClickListener {
            val text = editText.text.toString()
            if (text.isNotBlank()) {
                onPromptSaved(text)
                dialog.dismiss()
            } else {
                Toast.makeText(context, "Prompt cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }
}
