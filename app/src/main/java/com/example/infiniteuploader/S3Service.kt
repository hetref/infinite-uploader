package com.example.infiniteuploader

import aws.sdk.kotlin.runtime.auth.credentials.StaticCredentialsProvider
import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.model.DeleteObjectRequest
import aws.sdk.kotlin.services.s3.model.GetObjectRequest
import aws.sdk.kotlin.services.s3.model.ListObjectsV2Request
import aws.sdk.kotlin.services.s3.model.PutObjectRequest
import aws.sdk.kotlin.services.s3.presigners.presignGetObject
import aws.sdk.kotlin.services.s3.presigners.presignPutObject
import aws.smithy.kotlin.runtime.content.writeToFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.hours

sealed class S3Item {
    abstract val name: String
    data class File(val key: String, override val name: String, val size: Long?) : S3Item()
    data class Folder(val prefix: String, override val name: String) : S3Item()
}

class S3Service {
    private var s3Client: S3Client? = null
    private var currentCredentials: AwsCredentials? = null

    val currentBucket: String? get() = currentCredentials?.bucketName

    suspend fun connect(credentials: AwsCredentials) {
        withContext(Dispatchers.IO) {
            val s3 = S3Client {
                region = credentials.region
                this.credentialsProvider = StaticCredentialsProvider {
                    accessKeyId = credentials.accessKeyId
                    secretAccessKey = credentials.secretAccessKey
                }
            }
            // Test connection
            s3.listObjectsV2(ListObjectsV2Request {
                bucket = credentials.bucketName
                maxKeys = 1
            })
            s3Client = s3
            currentCredentials = credentials
        }
    }

    suspend fun listItems(prefix: String? = null): List<S3Item> = withContext(Dispatchers.IO) {
        val client = s3Client ?: throw IllegalStateException("S3Client not initialized")
        val bucketName = currentCredentials?.bucketName ?: throw IllegalStateException("Bucket name not set")

        val response = client.listObjectsV2(ListObjectsV2Request {
            bucket = bucketName
            this.prefix = prefix
            delimiter = "/"
        })

        val folders = response.commonPrefixes?.map { 
            S3Item.Folder(it.prefix ?: "", it.prefix?.removeSuffix("/")?.substringAfterLast("/") ?: "") 
        } ?: emptyList()

        val files = response.contents?.filter { it.key != prefix }?.map { 
            S3Item.File(it.key ?: "", it.key?.substringAfterLast("/") ?: "", it.size) 
        } ?: emptyList()

        folders + files
    }

    suspend fun getPresignedUrl(key: String): String = withContext(Dispatchers.IO) {
        val client = s3Client ?: throw IllegalStateException("S3Client not initialized")
        val bucketName = currentCredentials?.bucketName ?: throw IllegalStateException("Bucket name not set")

        val unsignedRequest = GetObjectRequest {
            bucket = bucketName
            this.key = key
        }
        val presignedRequest = client.presignGetObject(unsignedRequest, 1.hours)
        presignedRequest.url.toString()
    }

    suspend fun getPresignedUploadUrl(key: String): String = withContext(Dispatchers.IO) {
        val client = s3Client ?: throw IllegalStateException("S3Client not initialized")
        val bucketName = currentCredentials?.bucketName ?: throw IllegalStateException("Bucket name not set")

        val unsignedRequest = PutObjectRequest {
            bucket = bucketName
            this.key = key
        }
        val presignedRequest = client.presignPutObject(unsignedRequest, 1.hours)
        presignedRequest.url.toString()
    }

    suspend fun downloadFile(key: String, destinationFile: File) = withContext(Dispatchers.IO) {
        val client = s3Client ?: throw IllegalStateException("S3Client not initialized")
        val bucketName = currentCredentials?.bucketName ?: throw IllegalStateException("Bucket name not set")

        client.getObject(GetObjectRequest {
            bucket = bucketName
            this.key = key
        }) { resp ->
            resp.body?.writeToFile(destinationFile)
        }
    }

    suspend fun deleteFile(key: String) = withContext(Dispatchers.IO) {
        val client = s3Client ?: throw IllegalStateException("S3Client not initialized")
        val bucketName = currentCredentials?.bucketName ?: throw IllegalStateException("Bucket name not set")

        client.deleteObject(DeleteObjectRequest {
            bucket = bucketName
            this.key = key
        })
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        s3Client?.close()
        s3Client = null
        currentCredentials = null
    }
}
