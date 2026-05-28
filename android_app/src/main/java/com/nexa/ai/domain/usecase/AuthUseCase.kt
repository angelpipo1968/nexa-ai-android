package com.nexa.ai.domain.usecase

import com.nexa.ai.viewmodel.AuthManager
import com.nexa.ai.viewmodel.LoginResult
import com.nexa.ai.viewmodel.RegisterResult
import com.nexa.ai.viewmodel.UserData
import com.nexa.ai.data.SessionStore
import javax.inject.Inject

class AuthUseCase @Inject constructor(
    private val authManager: AuthManager,
    private val sessionStore: SessionStore
) {
    suspend fun login(email: String, pass: String): LoginResult = authManager.login(email, pass)
    suspend fun register(name: String, email: String, pass: String, confirm: String): RegisterResult = authManager.register(name, email, pass, confirm)
    suspend fun logout() {
        authManager.logout()
        sessionStore.clear()
    }
    suspend fun restoreUser(): UserData? = authManager.restoreUser()
}
