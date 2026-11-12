
package com.example.weatherapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnRegister: Button
    private lateinit var tvTitle: TextView

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            openWeatherApp()
            return
        }

        setContentView(R.layout.activity_login)

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnRegister = findViewById(R.id.btnRegister)
        tvTitle = findViewById(R.id.tvTitle)

        btnLogin.setOnClickListener {
            authenticateUser()
        }

        btnRegister.setOnClickListener {

            isRegisterMode = !isRegisterMode

            if (isRegisterMode) {
                tvTitle.text = "Create Account"
                btnLogin.text = "Register"
                btnRegister.text = "Already have an account? Sign In"
            } else {
                tvTitle.text = "Welcome Back!"
                btnLogin.text = "Sign In"
                btnRegister.text = "Create New Account"
            }
        }
    }

    private fun authenticateUser() {

        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(
                this,
                "Enter email and password",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(
                this,
                "Password must contain at least 6 characters",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        btnLogin.isEnabled = false

        if (isRegisterMode) {

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Registration successful",
                            Toast.LENGTH_SHORT
                        ).show()

                        openWeatherApp()
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.message ?: "Registration failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

        } else {

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->

                    btnLogin.isEnabled = true

                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Login successful",
                            Toast.LENGTH_SHORT
                        ).show()

                        openWeatherApp()
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.message ?: "Login failed",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    private fun openWeatherApp() {

        startActivity(
            Intent(this, MainActivity::class.java)
        )

        finish()
    }
}
