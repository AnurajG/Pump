package com.eomma.pump

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.android.user.api.IRegisterCallback
import com.thingclips.smart.android.user.bean.User
import com.thingclips.smart.sdk.api.IResultCallback

class RegisterActivity : AppCompatActivity() {

    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var verificationCodeInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var confirmPasswordInputLayout: TextInputLayout
    private lateinit var emailEditText: EditText
    private lateinit var verificationCodeEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var countryAutoComplete: MaterialAutoCompleteTextView
    private lateinit var registerButton: Button
    private lateinit var sendCodeButton: Button
    private lateinit var loadingProgressBar: ProgressBar
    private var countDownTimer: CountDownTimer? = null
    private var selectedCountryCode: String = "91" // Default country code
    private var isCodeSent = false
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        initializeViews()
        setupListeners()
        setupCountryDropdown()
        setupTextWatchers()
    }

    private fun initializeViews() {
        emailInputLayout = findViewById(R.id.til_email)
        verificationCodeInputLayout = findViewById(R.id.til_verification_code)
        passwordInputLayout = findViewById(R.id.til_password)
        confirmPasswordInputLayout = findViewById(R.id.til_confirm_password)
        emailEditText = findViewById(R.id.et_email)
        verificationCodeEditText = findViewById(R.id.et_verification_code)
        passwordEditText = findViewById(R.id.et_password)
        confirmPasswordEditText = findViewById(R.id.et_confirm_password)
        countryAutoComplete = findViewById(R.id.spinner_country)
        registerButton = findViewById(R.id.btn_register)
        sendCodeButton = findViewById(R.id.btn_send_code)
        loadingProgressBar = findViewById(R.id.loading_progress)

        // Set initial states
        verificationCodeInputLayout.visibility = View.GONE
        passwordInputLayout.visibility = View.GONE
        confirmPasswordInputLayout.visibility = View.GONE
        registerButton.isEnabled = false

        // Setup back button
        findViewById<ImageView>(R.id.backButton).setOnClickListener {
            onBackPressed()
        }
    }

    private fun setupListeners() {
        sendCodeButton.setOnClickListener {
            val email = emailEditText.text.toString()
            if (validateEmail(email)) {
                sendVerificationCode(email)
            }
        }

        registerButton.setOnClickListener {
            val email = emailEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()
            val verificationCode = verificationCodeEditText.text.toString()

            if (validateInput(email, password, confirmPassword, verificationCode)) {
                registerUser(email, password, verificationCode)
            }
        }
    }

    private fun setupTextWatchers() {
        // Email validation
        emailEditText.doOnTextChanged { text, _, _, _ ->
            emailInputLayout.error = null
            if (!text.isNullOrEmpty() && !isValidEmail(text.toString())) {
                emailInputLayout.error = "Please enter a valid email address"
            }
            validateForm()
        }

        // Verification code validation
        verificationCodeEditText.doOnTextChanged { text, _, _, _ ->
            verificationCodeInputLayout.error = null
            if (!text.isNullOrEmpty() && text.length != 6) {
                verificationCodeInputLayout.error = "Code must be 6 digits"
            }
            validateForm()
        }

        // Password validation
        passwordEditText.doOnTextChanged { text, _, _, _ ->
            passwordInputLayout.error = null
            if (!text.isNullOrEmpty() && text.length < 6) {
                passwordInputLayout.error = "Password must be at least 6 characters"
            }
            validatePasswordMatch()
            validateForm()
        }

        // Confirm password validation
        confirmPasswordEditText.doOnTextChanged { _, _, _, _ ->
            validatePasswordMatch()
            validateForm()
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

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            countryNames
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        countryAutoComplete.setAdapter(adapter)

        countryAutoComplete.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countryList[position].second
        }

        // Set default selection to India
        countryAutoComplete.setText(countryList[1].first, false)
        selectedCountryCode = countryList[1].second
    }

    private fun validateForm() {
        val email = emailEditText.text.toString()
        val code = verificationCodeEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        // Enable/disable send code button
        sendCodeButton.isEnabled = isValidEmail(email) && !isTimerRunning

        // Enable/disable register button based on all fields
        val isFormValid = isValidEmail(email) &&
                isCodeSent &&
                code.length == 6 &&
                password.length >= 6 &&
                password == confirmPassword
        registerButton.isEnabled = isFormValid
    }

    private fun validatePasswordMatch() {
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        confirmPasswordInputLayout.error = null
        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
            confirmPasswordInputLayout.error = "Passwords do not match"
        }
    }

    private fun validateInput(email: String, password: String, confirmPassword: String, code: String): Boolean {
        var isValid = true

        if (!isCodeSent) {
            Toast.makeText(this, "Please verify your email first", Toast.LENGTH_SHORT).show()
            return false
        }

        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            isValid = false
        } else if (!isValidEmail(email)) {
            emailInputLayout.error = "Please enter a valid email address"
            isValid = false
        }

        if (code.isEmpty()) {
            verificationCodeInputLayout.error = "Verification code is required"
            isValid = false
        } else if (code.length != 6) {
            verificationCodeInputLayout.error = "Invalid verification code"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordInputLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordInputLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordInputLayout.error = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordInputLayout.error = "Passwords do not match"
            isValid = false
        }

        return isValid
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            emailInputLayout.error = "Email is required"
            return false
        }
        if (!isValidEmail(email)) {
            emailInputLayout.error = "Please enter a valid email address"
            return false
        }
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun sendVerificationCode(email: String) {
        setLoadingState(true)
        sendCodeButton.isEnabled = false

        ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
            email,
            "",
            selectedCountryCode,
            1, // 1 for registration
            object : IResultCallback {
                override fun onSuccess() {
                    runOnUiThread {
                        setLoadingState(false)
                        handleVerificationCodeSent()
                    }
                }

                override fun onError(code: String, error: String) {
                    runOnUiThread {
                        setLoadingState(false)
                        sendCodeButton.isEnabled = true
                        handleVerificationCodeError(code, error)
                    }
                }
            }
        )
    }

    private fun handleVerificationCodeSent() {
        isCodeSent = true
        // Show verification code field and password fields
        verificationCodeInputLayout.visibility = View.VISIBLE
        passwordInputLayout.visibility = View.VISIBLE
        confirmPasswordInputLayout.visibility = View.VISIBLE

        // Enable all fields
        verificationCodeInputLayout.isEnabled = true
        passwordInputLayout.isEnabled = true
        confirmPasswordInputLayout.isEnabled = true

        Toast.makeText(this, "Verification code sent to your email", Toast.LENGTH_SHORT).show()
        startResendTimer()
    }

    private fun startResendTimer() {
        isTimerRunning = true
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                sendCodeButton.text = "Resend code in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                isTimerRunning = false
                sendCodeButton.isEnabled = true
                sendCodeButton.text = "Resend code"
                validateForm()
            }
        }.start()
    }

    private fun registerUser(email: String, password: String, code: String) {
        setLoadingState(true)

        ThingHomeSdk.getUserInstance().registerAccountWithEmail(
            selectedCountryCode,
            email,
            password,
            code,
            object : IRegisterCallback {
                override fun onSuccess(user: User) {
                    runOnUiThread {
                        setLoadingState(false)
                        handleRegistrationSuccess(email, user)
                    }
                }

                override fun onError(code: String, error: String) {
                    runOnUiThread {
                        setLoadingState(false)
                        handleRegistrationError(code, error)
                    }
                }
            }
        )
    }

    private fun setLoadingState(isLoading: Boolean) {
        loadingProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        emailInputLayout.isEnabled = !isLoading
        countryAutoComplete.isEnabled = !isLoading
        sendCodeButton.isEnabled = !isLoading && !isTimerRunning && isValidEmail(emailEditText.text.toString())

        if (isCodeSent) {
            verificationCodeInputLayout.isEnabled = !isLoading
            passwordInputLayout.isEnabled = !isLoading
            confirmPasswordInputLayout.isEnabled = !isLoading
        }

        registerButton.isEnabled = !isLoading && validateInput(
            emailEditText.text.toString(),
            passwordEditText.text.toString(),
            confirmPasswordEditText.text.toString(),
            verificationCodeEditText.text.toString()
        )
    }

    private fun handleRegistrationSuccess(email: String, user: User) {
        // Save user info
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putBoolean("isLoggedIn", true)
            putString("userEmail", email)
            putString("userId", user.uid)
            putString("countryCode", selectedCountryCode)
            apply()
        }

        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

        // Navigate to HomeActivity and clear the stack
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun handleRegistrationError(code: String, error: String) {
        val errorMsg = when (code) {
            "USER_EXISTS" -> "This email is already registered"
            "INVALID_VERIFY_CODE" -> "Invalid verification code"
            "VERIFY_CODE_EXPIRED" -> "Verification code has expired"
            "NETWORK_ERROR" -> "Network error. Please check your connection"
            else -> "Registration failed: $error"
        }
        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
    }

    private fun handleVerificationCodeError(code: String, error: String) {
        val errorMsg = when (code) {
            "USER_EXISTS" -> "This email is already registered"
            "SEND_VERIFY_CODE_FAILED" -> "Failed to send verification code. Please try again"
            "NETWORK_ERROR" -> "Network error. Please check your connection"
            else -> "Error sending code: $error"
        }
        Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}