package com.example.infiniteuploader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.InputStream

class S3UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val channelId = "s3_upload_channel"
    private val notificationId = params.id.hashCode()

    override suspend fun doWork(): Result {
        val fileUriString = inputData.getString("fileUri") ?: return Result.failure()
        val uploadUrl = inputData.getString("uploadUrl") ?: return Result.failure()
        val fileName = inputData.getString("fileName") ?: fileUriString.substringAfterLast("/")
        val fileUri = Uri.parse(fileUriString)

        createNotificationChannel()
        setForeground(createForegroundInfo(0, fileName))

        return try {
            val contentResolver = applicationContext.contentResolver
            val inputStream: InputStream = contentResolver.openInputStream(fileUri) ?: return Result.failure()
            val totalBytes = inputStream.available().toLong()

            val requestBody = object : RequestBody() {
                override fun contentType() = "application/octet-stream".toMediaTypeOrNull()
                override fun contentLength() = totalBytes
                override fun writeTo(sink: BufferedSink) {
                    var bytesWritten = 0L
                    val buffer = ByteArray(8192)
                    var read: Int
                    inputStream.use { input ->
                        while (input.read(buffer).also { read = it } != -1) {
                            sink.write(buffer, 0, read)
                            bytesWritten += read
                            val progress = if (totalBytes > 0) ((bytesWritten * 100) / totalBytes).toInt() else 0
                            
                            // Update progress in WorkData for UI
                            setProgressAsync(workDataOf("progress" to progress, "fileName" to fileName))
                            
                            // Update notification
                            notificationManager.notify(notificationId, createNotification(progress, fileName))
                        }
                    }
                }
            }

            val client = OkHttpClient()
            val request = Request.Builder()
                .url(uploadUrl)
                .put(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                notificationManager.notify(notificationId, createDoneNotification(fileName))
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "S3 Uploads",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int, fileName: String): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                createNotification(progress, fileName),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, createNotification(progress, fileName))
        }
    }

    private fun createNotification(progress: Int, fileName: String) =
        NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Uploading $fileName")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setOnlyAlertOnce(true)
            .build()

    private fun createDoneNotification(fileName: String): android.app.Notification {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Upload Done")
            .setContentText("$fileName uploaded successfully")
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
