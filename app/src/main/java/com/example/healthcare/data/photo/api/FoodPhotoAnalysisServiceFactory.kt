package com.example.healthcare.data.photo.api

import com.squareup.moshi.Moshi
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** HTTPS 백엔드와 앱의 기존 인증 클라이언트를 연결할 때 사용하는 생성 지점입니다. */
object FoodPhotoAnalysisServiceFactory {
    internal fun normalizeBaseUrl(baseUrl: String): HttpUrl {
        val normalized = baseUrl.trim().trimEnd('/') + "/"
        val parsed = normalized.toHttpUrlOrNull()
        require(parsed != null && parsed.isHttps) {
            "음식 사진 분석 서버는 유효한 HTTPS URL을 사용해야 합니다."
        }
        return parsed
    }

    fun create(
        baseUrl: String,
        authenticatedClient: OkHttpClient,
        moshi: Moshi = Moshi.Builder().build()
    ): FoodPhotoAnalysisApi {
        return Retrofit.Builder()
            .baseUrl(normalizeBaseUrl(baseUrl))
            .client(authenticatedClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FoodPhotoAnalysisApi::class.java)
    }
}
