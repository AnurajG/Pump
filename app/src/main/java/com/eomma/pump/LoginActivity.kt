package com.eomma.pump

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.bean.User
import android.util.Patterns
import androidx.core.widget.doOnTextChanged

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var countryAutoComplete: MaterialAutoCompleteTextView
    private lateinit var loginButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private var selectedCountryCode: String = "91" // Default country code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the user is already logged in
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        initializeViews()
        setupListeners()
        setupCountryDropdown()
    }

    private fun initializeViews() {
        emailInputLayout = findViewById(R.id.til_email)
        passwordInputLayout = findViewById(R.id.til_password)
        emailEditText = findViewById(R.id.et_email)
        passwordEditText = findViewById(R.id.et_password)
        countryAutoComplete = findViewById(R.id.spinner_country)
        loginButton = findViewById(R.id.btn_login)
        loadingProgressBar = findViewById(R.id.loading_progress)

        findViewById<TextView>(R.id.registerLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun setupListeners() {
        emailEditText.doOnTextChanged { text, _, _, _ ->
            emailInputLayout.error = null
            if (!text.isNullOrEmpty() && !isValidEmail(text.toString())) {
                emailInputLayout.error = "Please enter a valid email address"
            }
        }

        passwordEditText.doOnTextChanged { text, _, _, _ ->
            passwordInputLayout.error = null
            if (!text.isNullOrEmpty() && text.length < 6) {
                passwordInputLayout.error = "Password must be at least 6 characters"
            }
        }

        loginButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (validateInput(email, password)) {
                loginUser(email, password)
            }
        }
    }

    private fun setupCountryDropdown() {
        val countryList = listOf(
            "United States" to "1",
            "India" to "91",
            "United Kingdom" to "44",
            "Canada" to "1",
            "Australia" to "61",
            "Germany" to "49",
            "France" to "33",
            "Italy" to "39",
            "Spain" to "34",
            "Japan" to "81"
        )
        val countryNames = countryList.map { it.first }

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countryNames)
        countryAutoComplete.setAdapter(adapter)

        countryAutoComplete.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countryList[position].second
        }

        // Set default selection to India
        countryAutoComplete.setText(countryList[1].first, false)
        selectedCountryCode = countryList[1].second
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailInputLayout.error = "Please enter a valid email address"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun loginUser(email: String, password: String) {
        // Show loading state
        setLoadingState(true)

        ThingHomeSdk.getUserInstance().loginWithEmail(
            selectedCountryCode,
            email,
            password,
            object : ILoginCallback {
                override fun onSuccess(user: User) {
                    runOnUiThread {
                        setLoadingState(false)
                        handleLoginSuccess(email, user)
                    }
                }

                override fun onError(errorCode: String, errorMessage: String) {
                    runOnUiThread {
                        setLoadingState(false)
                        handleLoginError(errorCode, errorMessage)
                    }
                }
            }
        )
    }

    private fun setLoadingState(isLoading: Boolean) {
        loginButton.isEnabled = !isLoading
        loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        emailInputLayout.isEnabled = !isLoading
        passwordInputLayout.isEnabled = !isLoading
        countryAutoComplete.isEnabled = !isLoading
    }

    private fun handleLoginSuccess(email: String, user: User) {
        Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()

        // Save login state and user info
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("userEmail", email)
            putString("userId", user.uid)
            putString("countryCode", selectedCountryCode)
            apply()
        }

        // Navigate to HomeActivity
        startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
        finish()
    }

    private fun handleLoginError(errorCode: String, errorMessage: String) {
        val errorMsg = when (errorCode) {
            "USER_NOT_EXISTS" -> "User does not exist. Please register first."
            "INVALID_PASSWORD" -> "Invalid password. Please try again."
            "NETWORK_ERROR" -> "Network error. Please check your connection."
            else -> "Login failed: $errorMessage"
        }
        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_SHORT).show()
    }
}