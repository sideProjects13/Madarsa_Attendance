package com.example.madarsa_attendance

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
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
                isCancelable = false // Prevent user from dismissing failure dialogs accidentally
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
            // For success, automatically dismiss after a delay and finish the activity if requested
            Handler(Looper.getMainLooper()).postDelayed({
                dismiss()
                if (shouldFinishActivity) {
                    activity?.finish()
                }
            }, 2200) // 2.2 seconds delay
        } else {
            lottieView.setAnimation(R.raw.lottie_failure)
            // For failure, allow the user to dismiss it by tapping outside
            isCancelable = true
        }
    }

    override fun onStart() {
        super.onStart()
        // Make the dialog background transparent to show our rounded corners
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }
}