package com.example.madarsa_attendance

import android.content.Context
import android.graphics.Color // Or use ContextCompat for R.color resources
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
// import androidx.core.content.ContextCompat // If using R.color.your_color

class ColorableSpinnerAdapter<T>(
    context: Context,
    items: List<T>,
    private val desiredTextColor: Int // Pass Color.BLACK or ContextCompat.getColor(context, R.color.your_color)
) : ArrayAdapter<T>(context, android.R.layout.simple_spinner_item, items) {
    // The resource ID android.R.layout.simple_spinner_item is for the layout of the selected item view

    init {
        // This sets the layout resource for EACH ITEM in the dropdown list
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }

    // This method is called to get the View for the item that is currently selected and displayed in the Spinner (when it's not dropped down)
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) // Inflates android.R.layout.simple_spinner_item
        try {
            // The default android.R.layout.simple_spinner_item contains a TextView with ID @android:id/text1
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView?.setTextColor(desiredTextColor)
        } catch (e: Exception) {
            // Log or handle - very unlikely to fail with standard layouts
        }
        return view
    }

    // This method is called to get the View for each item in the dropdown list
    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) // Inflates android.R.layout.simple_spinner_dropdown_item
        try {
            // The default android.R.layout.simple_spinner_dropdown_item also contains a TextView (or CheckedTextView) with ID @android:id/text1
            val textView = view.findViewById<TextView>(android.R.id.text1)
            textView?.setTextColor(desiredTextColor)
        } catch (e: Exception) {
            // Log or handle
        }
        return view
    }
}