package com.example.madarsa_attendance

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage

class ManageTeachersActivity : AppCompatActivity() {

    private companion object {
        private const val TAG = "ManageTeachersActivity"
        private const val INTRO_EXTEND_DELAY_MS = 500L
        private const val INTRO_SHRINK_DELAY_MS = 3000L
    }

    private lateinit var recyclerViewManageTeachers: RecyclerView
    private lateinit var manageTeachersAdapter: ManageTeachersAdapter
    private lateinit var fabAddTeacher: ExtendedFloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvNoTeachers: TextView
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    private val teacherDisplayList = mutableListOf<TeacherSpinnerItem>()

    private val fabIntroHandler = Handler(Looper.getMainLooper())
    private var introExtendRunnable: Runnable? = null
    private var introShrinkRunnable: Runnable? = null

    private val teacherActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d(TAG, "teacherActionLauncher: resultCode = ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Add/Edit Teacher successful, reloading teachers.")
            loadTeachers()
        }
        if (::fabAddTeacher.isInitialized) {
            fabAddTeacher.shrink()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_teachers)

        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        val toolbar: MaterialToolbar = findViewById(R.id.manage_teachers_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerViewManageTeachers = findViewById(R.id.recyclerViewManageTeachers)
        fabAddTeacher = findViewById(R.id.fabAddTeacher)
        progressBar = findViewById(R.id.progressBarManageTeachers)
        tvNoTeachers = findViewById(R.id.tvNoTeachersManage)

        setupRecyclerView()
        setupFabInteraction()

        fabAddTeacher.shrink()

        if (savedInstanceState == null) {
            startFabIntroHintAnimation()
        }
    }

    private fun setupFabInteraction() {
        fabAddTeacher.setOnClickListener {
            cancelFabIntroHintAnimation()
            launchAddTeacherActivity()
        }
    }

    private fun launchAddTeacherActivity() {
        val intent = Intent(this, AddTeacherActivity::class.java)
        teacherActionLauncher.launch(intent)
        if (!isFinishing && !isDestroyed && ::fabAddTeacher.isInitialized) {
            fabAddTeacher.shrink()
        }
    }

    private fun startFabIntroHintAnimation() {
        if (::fabAddTeacher.isInitialized) {
            fabAddTeacher.shrink()
        }
        introExtendRunnable = Runnable {
            if (!isFinishing && !isDestroyed && ::fabAddTeacher.isInitialized) {
                fabAddTeacher.extend()
                introShrinkRunnable = Runnable {
                    if (!isFinishing && !isDestroyed && ::fabAddTeacher.isInitialized) {
                        fabAddTeacher.shrink()
                    }
                }
                fabIntroHandler.postDelayed(introShrinkRunnable!!, INTRO_SHRINK_DELAY_MS)
            }
        }
        fabIntroHandler.postDelayed(introExtendRunnable!!, INTRO_EXTEND_DELAY_MS)
    }

    private fun cancelFabIntroHintAnimation() {
        introExtendRunnable?.let { fabIntroHandler.removeCallbacks(it) }
        introShrinkRunnable?.let { fabIntroHandler.removeCallbacks(it) }
        introExtendRunnable = null
        introShrinkRunnable = null
    }

    override fun onResume() {
        super.onResume()
        loadTeachers()
        if (::fabAddTeacher.isInitialized && fabAddTeacher.isExtended && introExtendRunnable == null && introShrinkRunnable == null) {
            fabAddTeacher.shrink()
        }
    }

    override fun onPause() {
        super.onPause()
        cancelFabIntroHintAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelFabIntroHintAnimation()
    }

    private fun setupRecyclerView() {
        Log.d(TAG, "setupRecyclerView called.")
        manageTeachersAdapter = ManageTeachersAdapter(
            teachers = teacherDisplayList,
            onTeacherCardClick = { selectedTeacher ->
                Log.d(TAG, "Teacher card clicked: ${selectedTeacher.name}, ID: ${selectedTeacher.id}")
                val intent = Intent(this, TeacherOptionsActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                    putExtra("TEACHER_NAME", selectedTeacher.name)
                    putExtra("TEACHER_IMAGE_URL", selectedTeacher.profileImageUrl)
                }
                startActivity(intent)
            },
            onEditTeacherClick = { selectedTeacher ->
                    Log.d(TAG, "Edit menu clicked for teacher: ${selectedTeacher.id}")
                val intent = Intent(this, EditTeacherActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                }
                teacherActionLauncher.launch(intent)
            },
            onDeleteTeacherClick = { selectedTeacher ->
                Log.d(TAG, "Delete menu clicked for teacher: ${selectedTeacher.id}")
                confirmDeleteTeacher(selectedTeacher)
            }
        )
        recyclerViewManageTeachers.layoutManager = LinearLayoutManager(this)
        recyclerViewManageTeachers.adapter = manageTeachersAdapter
        Log.d(TAG, "RecyclerView adapter set.")
    }

    // confirmDeleteTeacher and deleteTeacherFromFirestore methods remain the same as previous response
    private fun confirmDeleteTeacher(teacher: TeacherSpinnerItem) {
        AlertDialog.Builder(this, R.style.AlertDialog_App_Monochrome)
            .setTitle("Delete Teacher")
            .setMessage("Are you sure you want to delete ${teacher.name}?\n\nWARNING: This will delete the teacher permanently. Associated students and attendance records will NOT be automatically reassigned or deleted by this action and might become orphaned.")
            .setPositiveButton("Delete") { _, _ ->
                deleteTeacherFromFirestore(teacher)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTeacherFromFirestore(teacher: TeacherSpinnerItem) {
        if (!::progressBar.isInitialized) return
        progressBar.visibility = View.VISIBLE

        if (!teacher.profileImageUrl.isNullOrEmpty()) {
            try {
                val imageRef = storage.getReferenceFromUrl(teacher.profileImageUrl)
                imageRef.delete().addOnSuccessListener {
                    Log.d(TAG, "Successfully deleted teacher profile image: ${teacher.profileImageUrl}")
                    deleteTeacherDocument(teacher.id)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to delete teacher profile image: ${teacher.profileImageUrl}", e)
                    Toast.makeText(this, "Note: Could not delete profile image.", Toast.LENGTH_SHORT).show()
                    deleteTeacherDocument(teacher.id)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting image from URL: ${teacher.profileImageUrl}", e)
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
                if (!::progressBar.isInitialized) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Teacher deleted successfully", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Teacher $teacherId deleted from Firestore.")
                loadTeachers()
            }
            .addOnFailureListener { e ->
                if (!::progressBar.isInitialized) return@addOnFailureListener
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error deleting teacher: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error deleting teacher $teacherId from Firestore", e)
            }
    }


    private fun loadTeachers() {
        // ... (This method remains the same as your provided correct version)
        if (!::progressBar.isInitialized) return
        progressBar.visibility = View.VISIBLE
        tvNoTeachers.visibility = View.GONE
        recyclerViewManageTeachers.visibility = View.GONE

        db.collection("teachers")
            .orderBy("teacherName", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { querySnapshot: QuerySnapshot? ->
                if (!::progressBar.isInitialized) return@addOnSuccessListener
                progressBar.visibility = View.GONE
                teacherDisplayList.clear()
                if (querySnapshot != null && !querySnapshot.isEmpty) {
                    Log.d(TAG, "loadTeachers: Found ${querySnapshot.size()} teachers.")
                    for (document in querySnapshot.documents) {
                        val teacherName = document.getString("teacherName")
                        val imageUrl = document.getString("profileImageUrl")
                        if (teacherName != null) {
                            teacherDisplayList.add(TeacherSpinnerItem(document.id, teacherName, imageUrl))
                        } else {
                            Log.w(TAG, "loadTeachers: Document ${document.id} missing teacherName field.")
                        }
                    }
                    recyclerViewManageTeachers.visibility = View.VISIBLE
                } else {
                    Log.d(TAG, "loadTeachers: No teachers found in Firestore.")
                    tvNoTeachers.visibility = View.VISIBLE
                }
                if(::manageTeachersAdapter.isInitialized) manageTeachersAdapter.updateData(teacherDisplayList)
            }
            .addOnFailureListener { e ->
                if (!::progressBar.isInitialized) return@addOnFailureListener
                progressBar.visibility = View.GONE
                tvNoTeachers.text = "Error loading teachers."
                tvNoTeachers.visibility = View.VISIBLE
                recyclerViewManageTeachers.visibility = View.GONE
                if(::manageTeachersAdapter.isInitialized) manageTeachersAdapter.updateData(emptyList())
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Error fetching teachers", e)
            }
    }
}