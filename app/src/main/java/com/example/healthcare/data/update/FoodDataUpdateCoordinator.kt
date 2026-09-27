package com.example.healthcare.data.update

import android.content.Context
import androidx.core.content.edit
import androidx.room.withTransaction
import com.example.healthcare.data.database.AppDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** 공식 K-FIND JSON 메타데이터만 읽습니다. 개인정보나 앱의 건강 정보는 전송하지 않습니다. */
class KFindPublicMetadataSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val metadataUrl: String = METADATA_URL
) : FoodDataUpdateSource {
    override suspend fun check(currentVersion: String): FoodDataUpdateCandidate? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(metadataUrl)
            .post(FormBody.Builder().build())
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("K-FIND metadata HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val latest = VERSION_REGEX.findAll(body).map { it.value }.maxOrNull()
                ?: error("K-FIND metadata version missing")
            if (latest <= currentVersion) return@withContext null
            // 공식 파일 다운로드는 소속·용도 입력 또는 서비스 키가 필요하므로 앱에서 우회하지 않습니다.
            FoodDataUpdateCandidate(
                sourceId = "K-FIND",
                version = latest,
                items = null,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified")
            )
        }
    }

    companion object {
        const val METADATA_URL = "https://various.foodsafetykorea.go.kr/nutrient/export/down/historyJson.do"
        private val VERSION_REGEX = Regex("20\\d{2}-\\d{2}-\\d{2}")
    }
}

class FoodDataUpdateStateStore(
    context: Context,
    preferenceName: String = "food_data_update"
) {
    private val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<FoodDataUpdateState> = _state.asStateFlow()

    fun save(value: FoodDataUpdateState) {
        preferences.edit {
            putString("activeVersion", value.activeVersion)
            putLong("lastCheckedAt", value.lastCheckedAt ?: 0L)
            putLong("lastUpdatedAt", value.lastUpdatedAt ?: 0L)
            putString("status", value.status.name)
            putString("message", value.message)
        }
        _state.value = value
    }

    private fun read(): FoodDataUpdateState = FoodDataUpdateState(
        activeVersion = preferences.getString("activeVersion", BUNDLED_VERSION) ?: BUNDLED_VERSION,
        lastCheckedAt = preferences.getLong("lastCheckedAt", 0L).takeIf { it > 0L },
        lastUpdatedAt = preferences.getLong("lastUpdatedAt", 0L).takeIf { it > 0L },
        status = runCatching {
            FoodDataUpdateStatus.valueOf(preferences.getString("status", null).orEmpty())
        }.getOrDefault(FoodDataUpdateStatus.IDLE),
        message = preferences.getString("message", "번들 음식 데이터를 사용 중").orEmpty()
    )

    companion object { const val BUNDLED_VERSION = "2026-08-28" }
}

class FoodDataUpdateCoordinator(
    private val database: AppDatabase,
    private val store: FoodDataUpdateStateStore,
    private val source: FoodDataUpdateSource = KFindPublicMetadataSource()
) {
    val state: StateFlow<FoodDataUpdateState> = store.state
    private val mutex = Mutex()

    suspend fun checkNow(): FoodDataUpdateState = mutex.withLock {
        val previous = state.value
        store.save(previous.copy(status = FoodDataUpdateStatus.CHECKING, message = "공식 데이터 버전 확인 중"))
        val now = System.currentTimeMillis()
        val result = runCatching {
            val candidate = source.check(previous.activeVersion)
            when {
                candidate == null -> previous.copy(
                    lastCheckedAt = now,
                    status = FoodDataUpdateStatus.UP_TO_DATE,
                    message = "현재 데이터가 최신입니다"
                )
                candidate.items == null -> previous.copy(
                    lastCheckedAt = now,
                    status = FoodDataUpdateStatus.SNAPSHOT_ONLY,
                    message = "새 공식 버전을 확인했지만 자동 파일 갱신은 공식 인증이 필요합니다"
                )
                else -> {
                    val validation = FoodDataUpdateValidator.validate(
                        candidate = candidate,
                        currentVersion = previous.activeVersion,
                        currentRowCount = database.foodItemDao().count()
                    )
                    check(validation.valid) { validation.errors.joinToString() }
                    database.withTransaction { database.foodItemDao().upsertAll(candidate.items) }
                    previous.copy(
                        activeVersion = candidate.version,
                        lastCheckedAt = now,
                        lastUpdatedAt = now,
                        status = FoodDataUpdateStatus.UPDATED,
                        message = "공식 음식 데이터를 갱신했습니다"
                    )
                }
            }
        }.getOrElse { error ->
            previous.copy(
                lastCheckedAt = now,
                status = FoodDataUpdateStatus.FAILED,
                message = "마지막 업데이트 확인 실패 · 기존 데이터를 사용 중 (${error.message ?: "알 수 없는 오류"})"
            )
        }
        store.save(result)
        result
    }
}
