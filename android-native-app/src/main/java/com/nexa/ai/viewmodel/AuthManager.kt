package com.nexa.ai.viewmodel

import android.app.Application
import com.nexa.ai.data.PersistedUser
import com.nexa.ai.data.UserStore
import kotlinx.coroutines.flow.first

/**
 * Manages authentication state and operations.
 */
class AuthManager(private val application: Application) {

    private val userStore = UserStore(application)

    /**
     * Restores the saved user session from DataStore.
     * Returns UserData if a session exists, null otherwise.
     */
    suspend fun restoreUser(): UserData? {
        val savedUser = userStore.currentUser.first() ?: return null
        return UserData(
            email = savedUser.email,
            displayName = savedUser.displayName,
            isLoggedIn = true
        )
    }

    /**
     * Attempts login with email/password.
     * Returns a LoginResult indicating success or failure.
     */
    suspend fun login(email: String, password: String): LoginResult {
        if (email.isBlank() || password.isBlank()) {
            return LoginResult.Error("fill_all")
        }
        if (!email.contains("@")) {
            return LoginResult.Error("invalid_email")
        }

        return try {
            val displayName = userStore.loginOrAutoRegister(email, password)
            val user = UserData(email = email, displayName = displayName, isLoggedIn = true)
            userStore.saveUser(PersistedUser(email, displayName))
            LoginResult.Success(user)
        } catch (e: Exception) {
            LoginResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Attempts to register a new account.
     * Returns a RegisterResult indicating success or failure.
     */
    suspend fun register(name: String, email: String, password: String, confirmPassword: String): RegisterResult {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            return RegisterResult.Error("fill_all")
        }
        if (!email.contains("@")) {
            return RegisterResult.Error("invalid_email")
        }
        if (password.length < 6) {
            return RegisterResult.Error("min_chars")
        }
        if (password != confirmPassword) {
            return RegisterResult.Error("passwords_no_match")
        }

        return try {
            val success = userStore.register(name, email, password)
            if (success) {
                val user = UserData(email = email, displayName = name, isLoggedIn = true)
                userStore.saveUser(PersistedUser(email, name))
                RegisterResult.Success(user)
            } else {
                RegisterResult.Error("email_taken")
            }
        } catch (e: Exception) {
            RegisterResult.Error("Error: ${e.message}")
        }
    }

    /**
     * Logs out the current user and clears saved data.
     */
    suspend fun logout() {
        userStore.clearUser()
    }
}

sealed class LoginResult {
    data class Success(val user: UserData) : LoginResult()
    data class Error(val messageKey: String) : LoginResult()
}

sealed class RegisterResult {
    data class Success(val user: UserData) : RegisterResult()
    data class Error(val messageKey: String) : RegisterResult()
}
