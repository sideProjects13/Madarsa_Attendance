package com.example.madarsa_attendance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import com.airbnb.lottie.LottieAnimationView

class StatusDialogFragment : DialogFragment() {

    companion object {
        private const val ARG_IS_SUCCESS = "is_success"
        private const val ARG_MESSAGE = "message"
        private const val ARG_FINISH_ACTIVITY = "finish_activity"

        fun newInstance(
            isSuccess: Boolean,
            message: String,
            finishActivityOnDismiss: Boolean = false
        ): StatusDialogFragment {
            val args = Bundle().apply {
                putBoolean(ARG_IS_SUCCESS, isSuccess)
                putString(ARG_MESSAGE, message)
                putBoolean(ARG_FINISH_ACTIVITY, finishActivityOnDismiss)
            }
            return StatusDialogFragment().apply {
                arguments = args
                // Default to not cancelable; we'll change it for failure cases.
                isCancelable = false
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lottieView = view.findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        val messageView = view.findViewById<TextView>(R.id.tvStatusMessage)

        val isSuccess = requireArguments().getBoolean(ARG_IS_SUCCESS)
        val message = requireArguments().getString(ARG_MESSAGE, "An error occurred.")
        val shouldFinishActivity = requireArguments().getBoolean(ARG_FINISH_ACTIVITY)

        messageView.text = message

        if (isSuccess) {
            lottieView.setAnimation(R.raw.lottie_success)

            // --- THIS IS THE NEW LOGIC ---
            // Allow the user to dismiss success dialogs by tapping anywhere.
            view.setOnClickListener {
                safeDismiss(shouldFinishActivity)
            }
            // --- END OF NEW LOGIC ---

            // Auto-dismiss after a delay as a fallback.
            Handler(Looper.getMainLooper()).postDelayed({
                safeDismiss(shouldFinishActivity)
            }, 2200)

        } else {
            lottieView.setAnimation(R.raw.lottie_failure)
            // For failure, also allow dismissing by tapping.
            isCancelable = true
            view.setOnClickListener {
                safeDismiss(false) // Never finish activity on failure dismiss
            }
        }
    }

    /**
     * A lifecycle-aware function to safely dismiss the dialog.
     */
    private fun safeDismiss(shouldFinishActivity: Boolean) {
        // Check if the fragment is still attached and in a valid state to prevent crashes.
        if (isAdded && viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            dismissAllowingStateLoss()
            if (shouldFinishActivity) {
                activity?.finish()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}