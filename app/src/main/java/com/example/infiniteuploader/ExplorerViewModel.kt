package com.example.infiniteuploader

import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File

sealed class ExplorerEvent {
    data class Error(val message: String) : ExplorerEvent()
    data class Message(val message: String) : ExplorerEvent()
    data class OpenUrl(val url: String) : ExplorerEvent()
}

class ExplorerViewModel(
    private val s3Service: S3Service,
    private val workManager: WorkManager
) : ViewModel() {

    var items by mutableStateOf<List<S3Item>>(emptyList())
    var currentPath by mutableStateOf<List<String>>(emptyList()) // List of folder names
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)

    val activeUploads = workManager.getWorkInfosByTagLiveData("upload")

    val currentBucket: String get() = s3Service.currentBucket ?: "Unknown Bucket"

    private val _events = MutableSharedFlow<ExplorerEvent>()
    val events = _events.asSharedFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        isLoading = true
        fetchItems()
    }

    fun refreshItems() {
        isRefreshing = true
        fetchItems()
    }

    private fun fetchItems() {
        viewModelScope.launch {
            try {
                val prefix = if (currentPath.isEmpty()) null else currentPath.joinToString("/") + "/"
                items = s3Service.listItems(prefix)
            } catch (e: Exception) {
                _events.emit(ExplorerEvent.Error(e.message ?: "Failed to load items"))
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    fun navigateTo(folder: S3Item.Folder) {
        currentPath = currentPath + folder.name
        loadItems()
    }

    fun navigateBack(index: Int) {
        currentPath = currentPath.take(index + 1)
        loadItems()
    }

    fun navigateToRoot() {
        currentPath = emptyList()
        loadItems()
    }

    fun viewFile(file: S3Item.File) {
        viewModelScope.launch {
            try {
                val url = s3Service.getPresignedUrl(file.key)
                _events.emit(ExplorerEvent.OpenUrl(url))
            } catch (e: Exception) {
                _events.emit(ExplorerEvent.Error(e.message ?: "Failed to get URL"))
            }
        }
    }

    fun downloadFile(file: S3Item.File) {
        viewModelScope.launch {
            try {
                val url = s3Service.getPresignedUrl(file.key)
                _events.emit(ExplorerEvent.OpenUrl(url)) // Open in browser for download
                _events.emit(ExplorerEvent.Message("Opening download link..."))
            } catch (e: Exception) {
                _events.emit(ExplorerEvent.Error(e.message ?: "Failed to get download URL"))
            }
        }
    }

    fun uploadFile(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                val prefix = if (currentPath.isEmpty()) "" else currentPath.joinToString("/") + "/"
                val key = prefix + fileName
                
                val uploadUrl = s3Service.getPresignedUploadUrl(key)
                
                val uploadWorkRequest = OneTimeWorkRequestBuilder<S3UploadWorker>()
                    .addTag("upload")
                    .setInputData(Data.Builder()
                        .putString("fileUri", uri.toString())
                        .putString("uploadUrl", uploadUrl)
                        .putString("fileName", fileName)
                        .build())
                    .build()
                
                workManager.enqueue(uploadWorkRequest)
                _events.emit(ExplorerEvent.Message("Upload started for $fileName"))
            } catch (e: Exception) {
                _events.emit(ExplorerEvent.Error(e.message ?: "Failed to start upload"))
            }
        }
    }

    fun deleteFile(file: S3Item.File) {
        viewModelScope.launch {
            try {
                s3Service.deleteFile(file.key)
                _events.emit(ExplorerEvent.Message("Deleted ${file.name}"))
                loadItems()
            } catch (e: Exception) {
                _events.emit(ExplorerEvent.Error(e.message ?: "Deletion failed"))
            }
        }
    }

    suspend fun disconnect() {
        s3Service.disconnect()
    }
}
