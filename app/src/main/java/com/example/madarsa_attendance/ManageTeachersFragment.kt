package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.madarsa_attendance.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage

class ManageTeachersFragment : Fragment() {

    private companion object {
        private const val TAG = "ManageTeachersFragment"
    }

    private lateinit var recyclerViewManageTeachers: RecyclerView
    private lateinit var manageTeachersAdapter: ManageTeachersAdapter
    private lateinit var fabAddTeacher: ExtendedFloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoTeachers: TextView
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private val teacherDisplayList = mutableListOf<TeacherSpinnerItem>()

    private val teacherActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Add/Edit Teacher successful, reloading teachers.")
            loadTeachers()
        }
        fabAddTeacher.shrink()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_manage_teachers_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        recyclerViewManageTeachers = view.findViewById(R.id.recyclerViewManageTeachers)
        fabAddTeacher = view.findViewById(R.id.fabAddTeacher)
        progressBar = view.findViewById(R.id.progressBarManageTeachers)
        tvNoTeachers = view.findViewById(R.id.tvNoTeachersManage)

        setupRecyclerView()
        setupFabInteraction()

        fabAddTeacher.shrink()
    }

    override fun onResume() {
        super.onResume()
        loadTeachers()
    }

    private fun setupFabInteraction() {
        fabAddTeacher.setOnClickListener {
            val intent = Intent(requireContext(), AddTeacherActivity::class.java)
            teacherActionLauncher.launch(intent)
        }
    }

    private fun setupRecyclerView() {
        manageTeachersAdapter = ManageTeachersAdapter(
            teachers = teacherDisplayList,
            onTeacherCardClick = { selectedTeacher ->
                val intent = Intent(requireContext(), TeacherOptionsActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                    putExtra("TEACHER_NAME", selectedTeacher.name)
                    putExtra("TEACHER_IMAGE_URL", selectedTeacher.profileImageUrl)
                }
                startActivity(intent)
            },
            onEditTeacherClick = { selectedTeacher ->
                val intent = Intent(requireContext(), EditTeacherActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                }
                teacherActionLauncher.launch(intent)
            },
            onDeleteTeacherClick = { selectedTeacher ->
                confirmDeleteTeacher(selectedTeacher)
            }
        )
        recyclerViewManageTeachers.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewManageTeachers.adapter = manageTeachersAdapter
    }

    private fun confirmDeleteTeacher(teacher: TeacherSpinnerItem) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext(), R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Teacher")
            .setMessage("Are you sure you want to delete ${teacher.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteTeacherFromFirestore(teacher)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTeacherFromFirestore(teacher: TeacherSpinnerItem) {
        if (!isAdded) return
        progressBar.visibility = View.VISIBLE
        // First delete image if it exists
        if (!teacher.profileImageUrl.isNullOrEmpty()) {
            try {
                val imageRef = storage.getReferenceFromUrl(teacher.profileImageUrl)
                imageRef.delete().addOnCompleteListener {
                    // Regardless of image deletion success, delete the document
                    deleteTeacherDocument(teacher.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing image URL for deletion", e)
                deleteTeacherDocument(teacher.id)
            }
        } else {
            deleteTeacherDocument(teacher.id)
        }
    }

    private fun deleteTeacherDocument(teacherId: String) {
        db.collection("teachers").document(teacherId)
            .delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Teacher deleted", Toast.LENGTH_SHORT).show()
                loadTeachers()
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error deleting teacher", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadTeachers() {
        if (!isAdded) return
        progressBar.visibility = View.VISIBLE
        tvNoTeachers.visibility = View.GONE
        recyclerViewManageTeachers.visibility = View.GONE

        db.collection("teachers")
            .orderBy("teacherName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot: QuerySnapshot? ->
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                teacherDisplayList.clear()
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    querySnapshot.documents.forEach { doc ->
                        teacherDisplayList.add(
                            TeacherSpinnerItem(
                                id = doc.id,
                                name = doc.getString("teacherName") ?: "N/A",
                                profileImageUrl = doc.getString("profileImageUrl")
                            )
                        )
                    }
                    recyclerViewManageTeachers.visibility = View.VISIBLE
                } else {
                    tvNoTeachers.visibility = View.VISIBLE
                }
                manageTeachersAdapter.updateData(teacherDisplayList)
            }
            .addOnFailureListener { e ->
                if (!isAdded) return@addOnFailureListener
                progressBar.visibility = View.GONE
                tvNoTeachers.text = "Error loading teachers."
                tvNoTeachers.visibility = View.VISIBLE
                manageTeachersAdapter.updateData(emptyList())
            }
    }
}