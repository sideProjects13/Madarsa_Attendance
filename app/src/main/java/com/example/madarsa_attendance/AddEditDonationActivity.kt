package com.example.madarsa_attendance

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class AddEditDonationActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_DONATION = "EXTRA_DONATION"
    }

    private lateinit var etDonorName: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var etMobile: TextInputEditText
    private lateinit var etPurpose: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var toolbar: MaterialToolbar

    private lateinit var db: FirebaseFirestore
    private var organizationId: String? = null
    private var editingDonation: DonationRecord? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_donation)

        db = FirebaseFirestore.getInstance()
        organizationId = FirebaseAuthManager.getOrganizationId(this)

        if (organizationId == null) {
            Toast.makeText(this, "Organization ID not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()

        editingDonation = intent.getSerializableExtra(EXTRA_DONATION) as? DonationRecord
        editingDonation?.let { populateUiForEdit(it) }

        btnSave.setOnClickListener { saveDonation() }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar_add_edit_donation)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        etDonorName = findViewById(R.id.et_donor_name)
        etAmount = findViewById(R.id.et_donation_amount)
        etMobile = findViewById(R.id.et_donor_mobile)
        etPurpose = findViewById(R.id.et_donation_purpose)
        btnSave = findViewById(R.id.btn_save_donation)
    }

    private fun populateUiForEdit(donation: DonationRecord) {
        toolbar.title = "Edit Donation"
        etDonorName.setText(donation.donorName)
        etAmount.setText(donation.amount.toString())
        etMobile.setText(donation.donorMobile)
        etPurpose.setText(donation.purpose)
    }

    private fun saveDonation() {
        val donorName = etDonorName.text.toString().trim()
        val amountStr = etAmount.text.toString().trim()
        val mobile = etMobile.text.toString().trim()
        val purpose = etPurpose.text.toString().trim()

        if (donorName.isEmpty() || amountStr.isEmpty()) {
            Toast.makeText(this, "Donor Name and Amount are required.", Toast.LENGTH_SHORT).show()
            return
        }

        val amount = amountStr.toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Please enter a valid amount.", Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = StatusDialogFragment.newInstance(true, "Saving...").apply { isCancelable = false }
        loadingDialog.show(supportFragmentManager, "saving")

        val collectionRef = db.collection("organizations").document(organizationId!!).collection("donations")

        val donationToSave = if (editingDonation == null) {
            // Creating a new donation
            val newDocRef = collectionRef.document()
            DonationRecord(newDocRef.id, donorName, amount, mobile, purpose, Date())
        } else {
            // Updating an existing one
            editingDonation!!.copy(donorName = donorName, amount = amount, donorMobile = mobile, purpose = purpose)
        }

        collectionRef.document(donationToSave.id).set(donationToSave)
            .addOnSuccessListener {
                loadingDialog.dismiss()
                Toast.makeText(this, "Donation saved successfully!", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                loadingDialog.dismiss()
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}