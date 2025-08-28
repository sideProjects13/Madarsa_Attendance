package com.example.madarsa_attendance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment

abstract class FullScreenWidthDialogFragment : DialogFragment() {

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // You might want to remove padding from the root layout of the dialog fragment's content view
        // to get a true "edge-to-edge" feel for the dialog if needed.
        // For now, let's just set the width.
        return super.onCreateView(inflater, container, savedInstanceState)
    }
}