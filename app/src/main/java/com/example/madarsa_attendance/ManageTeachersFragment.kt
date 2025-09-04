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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

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
    private var currentOrganizationId: String? = null

    private val teacherDisplayList = mutableListOf<TeacherSpinnerItem>()

    private val teacherActionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (!isAdded) return@registerForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            Log.d(TAG, "Add/Edit Teacher successful, reloading teachers.")
            loadTeachers()
        }
        if (::fabAddTeacher.isInitialized) {
            fabAddTeacher.shrink()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.activity_manage_teachers_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        currentOrganizationId = FirebaseAuthManager.getOrganizationId(requireContext())

        if (currentOrganizationId == null) {
            Toast.makeText(context, "Organization information missing. Please log in.", Toast.LENGTH_LONG).show()
            return
        }

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
            if (currentOrganizationId == null) {
                Toast.makeText(context, "Cannot add teacher: Organization ID missing.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(requireContext(), AddTeacherActivity::class.java)
            teacherActionLauncher.launch(intent)
        }
    }

    private fun setupRecyclerView() {
        manageTeachersAdapter = ManageTeachersAdapter(
            teachers = emptyList(),
            onTeacherCardClick = { selectedTeacher ->
                val intent = Intent(requireContext(), TeacherOptionsActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                    putExtra("TEACHER_NAME", selectedTeacher.name)
                    putExtra("TEACHER_IMAGE_URL", selectedTeacher.profileImageUrl)
                }
                startActivity(intent)
            },
            // --- THIS IS THE FIX ---
            // Provide the new onTeacherCardLongClick listener.
            // We can leave it empty since admins don't mark attendance here.
            onTeacherCardLongClick = { teacher ->
                // You could add functionality here later if you want, like showing a toast.
                // For now, it does nothing.
            },
            // --- END OF FIX ---
            onEditTeacherClick = { selectedTeacher ->
                val intent = Intent(requireContext(), EditTeacherActivity::class.java).apply {
                    putExtra("TEACHER_ID", selectedTeacher.id)
                }
                teacherActionLauncher.launch(intent)
            },
            onDeleteTeacherClick = { selectedTeacher ->
                (requireActivity() as MainActivity).confirmDeleteTeacher(selectedTeacher)
            }
        )
        recyclerViewManageTeachers.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewManageTeachers.adapter = manageTeachersAdapter
    }

    // --- FIX 2: Changed to public ---
    public fun loadTeachers() {
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