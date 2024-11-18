package com.eomma.pump

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.thingclips.smart.home.sdk.ThingHomeSdk
import com.thingclips.smart.android.user.api.ILoginCallback
import com.thingclips.smart.android.user.bean.User

class LoginActivity : AppCompatActivity() {

    private lateinit var countrySpinner: Spinner
    private lateinit var uidEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private var selectedCountryCode: String = "91" // Default country code

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the user is already logged in
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        if (sharedPreferences.getBoolean("isLoggedIn", false)) {
            // If already logged in, go straight to PumpScanActivity
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        countrySpinner = findViewById(R.id.spinner_country)
        uidEditText = findViewById(R.id.et_uid)
        passwordEditText = findViewById(R.id.et_password)
        loginButton = findViewById(R.id.btn_login)

        setupCountrySpinner()

        loginButton.setOnClickListener {
            val uid = uidEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (uid.isNotEmpty() && password.isNotEmpty()) {
                loginUser(uid, password)
            } else {
                Toast.makeText(this, "Please enter user id and password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupCountrySpinner() {
        // Sample list of countries with country codes
        val countryList = listOf(
            "United States" to "1",
            "India" to "91",
            "United Kingdom" to "44"
        )

        // Extract country names for display in the Spinner
        val countryNames = countryList.map { it.first }

        // Set up ArrayAdapter and attach it to the Spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, countryNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        countrySpinner.adapter = adapter

        // Set listener to update selectedCountryCode when a country is selected
        countrySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                selectedCountryCode = countryList[position].second
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Default to a country code if none is selected
                selectedCountryCode = "91"
            }
        }
    }

    private fun loginUser(userId: String, password: String) {
        // Login with user id and password, using the selected country code
        ThingHomeSdk.getUserInstance().loginOrRegisterWithUid(
            selectedCountryCode, // Pass the selected country code here
            userId,
            password,
            object : ILoginCallback {
                override fun onSuccess(user: User) {
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()

                    // Save login state to SharedPreferences
                    val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                    sharedPreferences.edit().putBoolean("isLoggedIn", true).apply()

                    // Navigate to PumpScanActivity
                    startActivity(Intent(this@LoginActivity, HomeActivity::class.java))
                    finish()
                }

                override fun onError(errorCode: String, errorMessage: String) {
                    Toast.makeText(this@LoginActivity, "Login failed: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}
