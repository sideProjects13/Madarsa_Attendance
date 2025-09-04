// DataRefreshEvent.kt
package com.example.madarsa_attendance

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/**
 * A singleton object to handle app-wide data refresh events.
 * This allows different Activities and Fragments to communicate without direct dependencies.
 */
object DataRefreshEvent {
    // Use the Event wrapper to ensure the refresh is only processed once.
    private val _events = MutableLiveData<Event<Boolean>>()
    val events: LiveData<Event<Boolean>> = _events

    /**
     * Call this from anywhere in the app to signal that lists/data should be refreshed.
     */
    fun triggerRefresh() {
        _events.value = Event(true)
    }
}