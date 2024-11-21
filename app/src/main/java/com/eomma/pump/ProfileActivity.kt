package com.eomma.pump

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.thingclips.smart.android.user.api.ILogoutCallback
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.sdk.api.IResultCallback

class ProfileActivity : AppCompatActivity() {

    private lateinit var profileImage: ImageView
    private lateinit var userName: TextView
    private lateinit var logoutButton: Button
    private lateinit var deleteAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        initializeViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initializeViews() {
        profileImage = findViewById(R.id.profileImage)
        userName = findViewById(R.id.userName)
        logoutButton = findViewById(R.id.logoutButton)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)
    }

    private fun setupClickListeners() {
        // Menu item clicks
        findViewById<View>(R.id.myBabyItem).setOnClickListener {
            // Handle My Baby click
            startActivity(Intent(this, BabyProfileActivity::class.java))
        }

        findViewById<View>(R.id.termsItem).setOnClickListener {
            // Handle Help Center click
            startActivity(Intent(this, TermsActivity::class.java))
        }

        findViewById<View>(R.id.aboutItem).setOnClickListener {
            // Handle About click
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<View>(R.id.appSettingsItem).setOnClickListener {
            // Handle Settings click
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Logout button click
        logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }

        // Delete account button click
        deleteAccountButton.setOnClickListener {
            showDeleteAccountConfirmationDialog()
        }
    }

    private fun loadUserData() {
        // Get current user info from ThingHomeSdk
        val currentUser = ThingHomeSdk.getUserInstance().user
        currentUser?.let { user ->
            userName.text = user.nickName ?: "User"
            //userEmail.text = user.email ?: user.mobile ?: "No email"
        }
    }

    private fun shareApp() {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check out eomma pump app: https://play.google.com/store/apps/details?id=${packageName}")
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> logout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun showDeleteAccountConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun logout() {
        ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback {
            override fun onSuccess() {
                // Clear shared preferences
                getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()

                // Navigate to login screen
                startActivity(Intent(this@ProfileActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }

            override fun onError(code: String, error: String) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Logout failed: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun deleteAccount() {
        ThingHomeSdk.getUserInstance().cancelAccount(object : IResultCallback {
            override fun onSuccess() {
                // Clear shared preferences
                getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()

                // Navigate to login screen
                startActivity(Intent(this@ProfileActivity, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }

            override fun onError(code: String, error: String) {
                runOnUiThread {
                    Toast.makeText(this@ProfileActivity, "Failed to delete account: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}