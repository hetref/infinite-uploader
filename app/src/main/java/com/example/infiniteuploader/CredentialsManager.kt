package com.example.infiniteuploader

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class AwsCredentials(
    val accessKeyId: String,
    val secretAccessKey: String,
    val bucketName: String,
    val region: String
)

class CredentialsManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(credentials: AwsCredentials) {
        sharedPreferences.edit().apply {
            putString("access_key_${credentials.accessKeyId}", credentials.accessKeyId)
            putString("secret_key_${credentials.accessKeyId}", credentials.secretAccessKey)
            // Store a list of saved access keys
            val savedKeys = getSavedAccessKeys().toMutableSet()
            savedKeys.add(credentials.accessKeyId)
            putStringSet("saved_access_keys", savedKeys)
            apply()
        }
    }

    fun getSavedAccessKeys(): Set<String> {
        return sharedPreferences.getStringSet("saved_access_keys", emptySet()) ?: emptySet()
    }

    fun getCredentials(accessKeyId: String): Pair<String, String>? {
        val accessKey = sharedPreferences.getString("access_key_$accessKeyId", null)
        val secretKey = sharedPreferences.getString("secret_key_$accessKeyId", null)
        return if (accessKey != null && secretKey != null) {
            accessKey to secretKey
        } else {
            null
        }
    }
}
