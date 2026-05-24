package com.example.project.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.project.model.User
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isLoggedIn(): Boolean = prefs.contains(KEY_TOKEN)

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getCurrentUser(): User? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        return User(
            id = id,
            name = prefs.getString(KEY_USER_NAME, "") ?: "",
            email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        )
    }

    fun login(email: String, password: String): Result<User> {
        if (email.isBlank() || password.length < 3) {
            return Result.failure(Exception("Неверный email или пароль"))
        }

        val existingId = prefs.getString(KEY_USER_ID, null)
        val existingPassword = prefs.getString("${KEY_USER_ID}_pw", null)

        if (existingId != null && existingPassword != null) {
            if (password != existingPassword) {
                return Result.failure(Exception("Неверный пароль"))
            }
            val name = prefs.getString(KEY_USER_NAME, "") ?: ""
            val savedEmail = prefs.getString(KEY_USER_EMAIL, "") ?: ""
            return Result.success(User(existingId, name, savedEmail))
        }

        return Result.failure(Exception("Пользователь не найден. Зарегистрируйтесь сначала."))
    }

    fun register(name: String, email: String, password: String): Result<User> {
        if (name.isBlank() || email.isBlank() || password.length < 3) {
            return Result.failure(Exception("Заполните все поля (пароль минимум 3 символа)"))
        }

        val userId = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .putString("${KEY_USER_ID}_pw", password)
            .putString(KEY_TOKEN, "mock_token_$userId")
            .apply()

        return Result.success(User(userId, name, email))
    }

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun updateProfile(name: String, email: String) {
        prefs.edit()
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "contexthub_auth"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
    }
}
