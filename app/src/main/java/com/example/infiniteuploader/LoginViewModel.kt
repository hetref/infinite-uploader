package com.example.infiniteuploader

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

sealed class LoginEvent {
    object Success : LoginEvent()
    data class Error(val message: String) : LoginEvent()
}

class LoginViewModel(
    private val credentialsManager: CredentialsManager,
    private val s3Service: S3Service
) : ViewModel() {

    var accessKeyId by mutableStateOf("")
    var secretAccessKey by mutableStateOf("")
    var bucketName by mutableStateOf("")
    var region by mutableStateOf("us-east-1")
    var saveCredentials by mutableStateOf(false)
    var isLoading by mutableStateOf(false)

    private val _events = MutableSharedFlow<LoginEvent>()
    val events = _events.asSharedFlow()

    val savedAccessKeys = credentialsManager.getSavedAccessKeys()

    fun onAccessKeySelected(key: String) {
        credentialsManager.getCredentials(key)?.let { (access, secret) ->
            accessKeyId = access
            secretAccessKey = secret
        }
    }

    fun connect() {
        if (accessKeyId.isBlank() || secretAccessKey.isBlank() || bucketName.isBlank()) {
            viewModelScope.launch { _events.emit(LoginEvent.Error("Please fill all fields")) }
            return
        }

        isLoading = true
        viewModelScope.launch {
            try {
                val creds = AwsCredentials(accessKeyId, secretAccessKey, bucketName, region)
                s3Service.connect(creds)
                
                if (saveCredentials) {
                    credentialsManager.saveCredentials(creds)
                }
                
                _events.emit(LoginEvent.Success)
            } catch (e: Exception) {
                _events.emit(LoginEvent.Error(e.message ?: "Connection failed"))
            } finally {
                isLoading = false
            }
        }
    }
}
