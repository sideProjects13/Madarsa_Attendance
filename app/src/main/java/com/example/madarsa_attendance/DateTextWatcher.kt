package com.example.madarsa_attendance

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.util.Calendar

class DateTextWatcher(private val editText: EditText) : TextWatcher {
    private var current = ""
    private val currentYear = Calendar.getInstance().get(Calendar.YEAR)

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        val text = s.toString()
        if (text == current) {
            return
        }

        editText.removeTextChangedListener(this)

        var clean = text.replace("[^\\d]".toRegex(), "")
        val originalClean = clean

        // Perform real-time validation and correction
        if (clean.length >= 2) {
            var day = clean.substring(0, 2).toIntOrNull() ?: 1
            if (day == 0) day = 1
            if (day > 31) day = 31
            clean = String.format("%02d", day) + clean.substring(2)
        }

        if (clean.length >= 4) {
            var month = clean.substring(2, 4).toIntOrNull() ?: 1
            if (month == 0) month = 1
            if (month > 12) month = 12
            clean = clean.substring(0, 2) + String.format("%02d", month) + clean.substring(4)
        }

        if (clean.length >= 8) {
            var year = clean.substring(4, 8).toIntOrNull() ?: currentYear
            if (year > currentYear) year = currentYear
            clean = clean.substring(0, 4) + String.format("%04d", year)
        }

        // Only keep the length the user has typed so far
        clean = if (clean.length > originalClean.length) {
            clean.substring(0, originalClean.length)
        } else {
            clean
        }

        val sb = StringBuilder(clean)

        if (sb.length > 2) sb.insert(2, "-")
        if (sb.length > 5) sb.insert(5, "-")
        if (sb.length > 10) sb.setLength(10)

        current = sb.toString()
        editText.setText(current)
        editText.setSelection(current.length)

        editText.addTextChangedListener(this)
    }
}