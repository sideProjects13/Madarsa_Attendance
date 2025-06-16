package com.example.madarsa_attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

open class Event<out T>(private val content: T) {
    var hasBeenHandled = false
        private set
    fun getContentIfNotHandled(): T? {
        return if (hasBeenHandled) {
            null
        } else {
            hasBeenHandled = true
            content
        }
    }
    @Suppress("unused")
    fun peekContent(): T = content
}

class TeacherDataViewModel : ViewModel() {
    private val _studentsDataMightHaveChanged = MutableLiveData<Event<Unit>>() // Correct name
    val studentsDataMightHaveChanged: LiveData<Event<Unit>> get() = _studentsDataMightHaveChanged // Correct name

    fun notifyStudentDataChanged() {
        _studentsDataMightHaveChanged.value = Event(Unit)
    }
}