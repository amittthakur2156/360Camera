package com.example.threesixtycamera.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object SupabaseUploader {

    private val client =
        OkHttpClient()

    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )

    fun uploadImage(
        context: Context,
        uri: Uri,
        remotePath: String,
        callback: (Boolean, String?) -> Unit
    ) {

        scope.launch {

            try {

                val bytes =
                    context.contentResolver
                        .openInputStream(uri)
                        ?.use {
                            it.readBytes()
                        }

                if (bytes == null) {

                    callback(
                        false,
                        "Could not read image stream"
                    )

                    return@launch
                }

                val result =
                    uploadBytes(
                        bytes,
                        remotePath
                    )

                if (result.isSuccess) {

                    callback(
                        true,
                        result.getOrNull()
                    )

                } else {

                    callback(
                        false,
                        result.exceptionOrNull()
                            ?.message
                    )
                }

            } catch (e: Exception) {

                callback(
                    false,
                    e.message
                )
            }
        }
    }

    suspend fun uploadBytes(
        bytes: ByteArray,
        remotePath: String
    ): Result<String> =
        withContext(Dispatchers.IO) {

            try {

                val baseUrl =
                    SupabaseConfig
                        .SUPABASE_URL
                        .trimEnd('/')

                val anonKey =
                    SupabaseConfig
                        .SUPABASE_ANON_KEY

                val bucket =
                    SupabaseConfig
                        .BUCKET_NAME

                if (
                    baseUrl.isBlank() ||
                    anonKey.isBlank()
                ) {

                    return@withContext Result.failure(
                        IllegalStateException(
                            "Supabase URL/key not configured"
                        )
                    )
                }

                if (bucket.isBlank()) {

                    return@withContext Result.failure(
                        IllegalStateException(
                            "Supabase bucket name is empty"
                        )
                    )
                }

                if (remotePath.isBlank()) {

                    return@withContext Result.failure(
                        IllegalArgumentException(
                            "Remote file path is empty"
                        )
                    )
                }

                val cleanPath =
                    remotePath
                        .trim('/')
                        .replace(
                            " ",
                            "_"
                        )

                val uploadUrl =
                    "$baseUrl/storage/v1/object/" +
                            "$bucket/$cleanPath"

                val body =
                    bytes.toRequestBody(
                        "image/jpeg".toMediaType()
                    )

                /*
                 * IMPORTANT:
                 * No x-upsert header here.
                 *
                 * The app creates unique timestamped filenames,
                 * so only INSERT permission is needed.
                 */
                val request =
                    Request.Builder()
                        .url(uploadUrl)
                        .post(body)
                        .addHeader(
                            "Authorization",
                            "Bearer $anonKey"
                        )
                        .addHeader(
                            "apikey",
                            anonKey
                        )
                        .addHeader(
                            "Content-Type",
                            "image/jpeg"
                        )
                        .addHeader(
                            "Cache-Control",
                            "3600"
                        )
                        .build()

                client
                    .newCall(request)
                    .execute()
                    .use { response ->

                        if (
                            response.isSuccessful
                        ) {

                            return@withContext Result.success(
                                uploadUrl
                            )
                        }

                        val errorBody =
                            response.body
                                ?.string()
                                ?.take(2000)
                                ?: "No response body"

                        Result.failure(
                            IOException(
                                "Upload failed: " +
                                        "${response.code} " +
                                        "${response.message}\n" +
                                        errorBody
                            )
                        )
                    }

            } catch (e: Exception) {

                Result.failure(e)
            }
        }
}
