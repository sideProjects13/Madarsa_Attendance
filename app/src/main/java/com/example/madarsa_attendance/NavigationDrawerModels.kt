package com.example.madarsa_attendance

sealed class NavigationItem {
    data class SingleItem(
        val id: Int,
        val title: String,
        val iconResId: Int
    ) : NavigationItem()

    data class SingleItemWithDivider(
        val id: Int,
        val title: String,
        val iconResId: Int
    ) : NavigationItem()

    data class Header(
        val title: String,
        val iconResId: Int,
        var isExpanded: Boolean = false,
        val children: List<Child>
    ) : NavigationItem()

    // --- CHANGE: The iconResId property has been removed ---
    data class Child(
        val id: Int,
        val title: String
        // No icon here anymore
    ) : NavigationItem()
}