package com.example.madarsa_attendance

enum class ReportColumn(val title: String, val id: Int) {
    REG_NO("Reg. No", R.id.cb_reg_no),
    STUDENT_NAME("Student Name", R.id.cb_student_name),
    PARENT_NAME("Parent Name", R.id.cb_parent_name),
    PARENT_MOBILE("Parent Mobile", R.id.cb_parent_mobile),
    ALTERNATE_MOBILE("Alt. Mobile", R.id.cb_alternate_mobile),
    GENDER("Gender", R.id.cb_gender),
    DOB("Birth Date", R.id.cb_dob),
    ADMISSION_DATE("Admission Date", R.id.cb_admission_date),
    MONTHLY_FEE("Fee", R.id.cb_monthly_fee),
    TEACHER_NAME("Class", R.id.cb_teacher_name);

    companion object {
        fun fromId(id: Int): ReportColumn? = values().find { it.id == id }
    }
}