package com.example.madarsa_attendance

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieAnimationView

class SplashActivity : AppCompatActivity() {

    // Declare Views
    private lateinit var lottieAnimation: LottieAnimationView
    private lateinit var appTitle: TextView
    private lateinit var appSubtitle: TextView
    private lateinit var creatorText: TextView
    private lateinit var geometricPatternTop: ImageView
    private lateinit var geometricPatternBottom: ImageView
    private lateinit var rootView: View

    private val SPLASH_DURATION = 3000L // 3 seconds total

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set theme before inflating views
        setTheme(R.style.Theme_Madarsa_Attendance_PureMonochrome)
        setContentView(R.layout.activity_splash)

        // Initialize Views
        rootView = findViewById(android.R.id.content)
        lottieAnimation = findViewById(R.id.lottieAnimation)
        appTitle = findViewById(R.id.appTitle)
        appSubtitle = findViewById(R.id.appSubtitle)
        creatorText = findViewById(R.id.creatorText)
        geometricPatternTop = findViewById(R.id.geometricPatternTop)
        geometricPatternBottom = findViewById(R.id.geometricPatternBottom)

        // Make status bar transparent
        window.statusBarColor = ContextCompat.getColor(this, android.R.color.transparent)
        window.navigationBarColor = ContextCompat.getColor(this, android.R.color.transparent)

        // Hide action bar
        supportActionBar?.hide()

        setupAnimations()
    }

    private fun setupAnimations() {
        // Initial state - invisible
        lottieAnimation.alpha = 0f
        appTitle.alpha = 0f
        appSubtitle.alpha = 0f
        creatorText.alpha = 0f
        geometricPatternTop.alpha = 0.3f
        geometricPatternBottom.alpha = 0.3f

        // Start the animation sequence
        Handler(Looper.getMainLooper()).postDelayed({
            // Step 1: Fade in geometric patterns
            fadeInView(geometricPatternTop, 0, 600)
            fadeInView(geometricPatternBottom, 100, 600)

            // Step 2: Start Lottie animation and fade in
            Handler(Looper.getMainLooper()).postDelayed({
                lottieAnimation.alpha = 1f
                lottieAnimation.playAnimation()

                // Step 3: Fade in app title and subtitle
                Handler(Looper.getMainLooper()).postDelayed({
                    fadeInView(appTitle, 0, 800)
                    fadeInView(appSubtitle, 200, 800)

                    // Step 4: Fade in creator text
                    Handler(Looper.getMainLooper()).postDelayed({
                        fadeInView(creatorText, 0, 600)

                        // Step 5: Navigate to main activity
                        Handler(Looper.getMainLooper()).postDelayed({
                            navigateToMain()
                        }, 1000)
                    }, 500)
                }, 1000) // Wait for Lottie to complete first part
            }, 300)
        }, 300)
    }

    private fun fadeInView(view: View, delay: Long, duration: Long) {
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setStartDelay(delay)
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun navigateToMain() {
        // Add exit animation
        val exitAnimator = ValueAnimator.ofFloat(1f, 0f)
        exitAnimator.duration = 500
        exitAnimator.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Float
            rootView.alpha = alpha
        }
        exitAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
        })
        exitAnimator.start()
    }

    override fun onPause() {
        super.onPause()
        lottieAnimation.pauseAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (lottieAnimation.isAnimating) {
            lottieAnimation.resumeAnimation()
        }
    }
}