package com.example.madarsa_attendance

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class TeacherOptionsPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val teacherId: String,
    private val teacherName: String
) : FragmentStateAdapter(fragmentActivity) {

    // Updated tab titles: Manage Class, Attendance, Payments
    val tabTitles = arrayOf("Attendance", "Manage Class", "Payments") // Order from previous step

    override fun getItemCount(): Int = tabTitles.size

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TakeAttendanceFragment.newInstance(teacherId, teacherName)
            1 -> ManageClassFragment.newInstance(teacherId, teacherName)
            2 -> PaymentSummaryFragment.newInstance(teacherId, teacherName)
            else -> throw IllegalArgumentException("Invalid position for ViewPager: $position")
        }
    }
}