package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.button.MaterialButton // Keep if other MaterialButtons are used here
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.firebase.storage.FirebaseStorage // Import FirebaseStorage

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
    private lateinit var storage: FirebaseStorage // Ensure Storage is initialized
    private var currentOrganizationId: String? = null

    // REMOVED: private lateinit var btnLogout: MaterialButton

    private val teacherDisplayList = mutableListOf<TeacherSpinnerItem>()

    private val teacherActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult // Ensure fragment is still attached
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Add/Edit Teacher successful, reloading teachers.")
            loadTeachers()
        }
        if (::fabAddTeacher.isInitialized) { // Check if FAB is initialized before shrinking
            fabAddTeacher.shrink()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_manage_teachers_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance() // Initialize FirebaseStorage
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())

        if (currentOrganizationId == null) {
            Toast.makeText(context, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            // Consider disabling UI elements or redirecting to login activity from main activity
            return
        }

        recyclerViewManageTeachers = view.findViewById(R.id.recyclerViewManageTeachers)
        fabAddTeacher = view.findViewById(R.id.fabAddTeacher)
        progressBar = view.findViewById(R.id.progressBarManageTeachers)
        tvNoTeachers = view.findViewById(R.id.tvNoTeachersManage)
        // REMOVED: btnLogout = view.findViewById(R.id.btnLogout)

        setupRecyclerView()
        setupFabInteraction()
        // REMOVED: setupLogoutButton()

        fabAddTeacher.shrink()
    }

    override fun onResume() {
        super.onResume()
        loadTeachers()
    }

    private fun setupFabInteraction() {
        fabAddTeacher.setOnClickListener {
            if (currentOrganizationId == null) {
                Toast.makeText(context, "Cannot add teacher: Organization ID missing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), AddTeacherActivity::class.java)
            teacherActionLauncher.launch(intent)
        }
    }

    // REMOVED: setupLogoutButton() method

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
        if (!isAdded || currentOrganizationId == null) return
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
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        if (!teacher.profileImageUrl.isNullOrEmpty()) {
            try {
                // Use storage.getReferenceFromUrl for Cloudinary/Firebase Storage URLs
                // Note: If using Cloudinary, ensure the URL is directly resolvable or use Cloudinary's SDK for deletion.
                // Assuming it's Firebase Storage URL for direct deletion via FirebaseStorage SDK
                val imageRef = storage.getReferenceFromUrl(teacher.profileImageUrl)
                imageRef.delete().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d(TAG, "Successfully deleted teacher profile image: ${teacher.profileImageUrl}")
                    } else {
                        Log.e(TAG, "Failed to delete teacher profile image: ${teacher.profileImageUrl}", task.exception)
                        Toast.makeText(context, "Note: Could not delete profile image from storage.", Toast.LENGTH_SHORT).show()
                    }
                    deleteTeacherDocument(teacher.id) // Always attempt to delete document
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image URL for deletion: ${teacher.profileImageUrl}", e)
                Toast.makeText(context, "Note: Invalid image URL for deletion.", Toast.LENGTH_SHORT).show()
                deleteTeacherDocument(teacher.id) // Fallback to deleting document only
            }
        } else {
            deleteTeacherDocument(teacher.id)
        }
    }

    private fun deleteTeacherDocument(teacherId: String) {
        if (!isAdded || currentOrganizationId == null) return
        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers").document(teacherId)
            .delete()
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Teacher deleted", Toast.LENGTH_SHORT).show()
                loadTeachers()
            }
            .addOnFailureListener { e ->
                if (!isAdded)
                progressBar.visibility = View.GONE
                Toast.makeText(requireContext(), "Error deleting teacher", Toast.LENGTH_LONG).show()
            }
    }

    private fun loadTeachers() {
        if (!isAdded || currentOrganizationId == null) return
        progressBar.visibility = View.VISIBLE
        tvNoTeachers.visibility = View.GONE
        recyclerViewManageTeachers.visibility = View.GONE

        db.collection("organizations").document(currentOrganizationId!!)
            .collection("teachers")
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
                if (!isAdded)
                progressBar.visibility = View.GONE
                tvNoTeachers.text = "Error loading teachers."
                tvNoTeachers.visibility = View.VISIBLE
                manageTeachersAdapter.updateData(emptyList())
            }
    }
}