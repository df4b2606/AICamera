package com.example.aicameranew.ui.home



import android.app.AlertDialog

import android.app.Dialog

import android.os.Bundle

import android.view.Gravity

import androidx.fragment.app.DialogFragment



class AiResultDialog(private val result: String) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        val dialog = AlertDialog.Builder(requireContext())

            .setTitle("AI Analysis Result")

            .setMessage(result)

            .setPositiveButton("OK", null)

            .create()



// 可点击外部取消

        dialog.setCanceledOnTouchOutside(true)

        return dialog

    }



    override fun onStart() {

        super.onStart()

        dialog?.window?.apply {

            val width = (resources.displayMetrics.widthPixels * 0.9).toInt()

            val height = (resources.displayMetrics.heightPixels * 0.7).toInt()

            setLayout(width, height)



// 向上偏移

            val params = attributes

            params.gravity = Gravity.CENTER

            params.y = -100

            attributes = params



// 可选：设置背景透明

            setBackgroundDrawableResource(android.R.color.transparent)

        }

    }

}