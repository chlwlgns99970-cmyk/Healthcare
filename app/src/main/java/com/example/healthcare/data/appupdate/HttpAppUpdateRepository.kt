package com.example.healthcare.data.appupdate

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class HttpAppUpdateRepository(
    private val client: OkHttpClient,
    private val endpoint: String,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
) : AppUpdateRepository {
    private val adapter = moshi.adapter(AppReleaseMetadata::class.java)

    override suspend fun getLatestRelease(): AppReleaseMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update feed HTTP ${response.code}" }
            val payload = response.body?.string().orEmpty()
            val metadata = adapter.fromJson(payload) ?: error("Empty update metadata")
            check(AppReleaseMetadataValidator.isValid(metadata)) { "Invalid update metadata" }
            metadata.normalized()
        }
    }
}
