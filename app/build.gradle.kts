import java.net.URI
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
}

val foodAnalysisBaseUrl = providers.gradleProperty("FOOD_ANALYSIS_BASE_URL")
    .orElse(providers.environmentVariable("FOOD_ANALYSIS_BASE_URL"))
    .orNull
    ?.trim()
    .orEmpty()

val releaseStorePath = providers.gradleProperty("TODAY_RELEASE_STORE_FILE").orNull.orEmpty()
val releaseStorePassword = providers.gradleProperty("TODAY_RELEASE_STORE_PASSWORD").orNull.orEmpty()
val releaseKeyPassword = providers.gradleProperty("TODAY_RELEASE_KEY_PASSWORD").orNull.orEmpty()
val releaseSigningConfigured = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyPassword
).all { it.isNotBlank() }

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.example.healthcare"
    testBuildType = "qa"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.healthcare"
        minSdk = 24
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"

        buildConfigField("String", "FOOD_ANALYSIS_BASE_URL", foodAnalysisBaseUrl.asBuildConfigString())
        buildConfigField(
            "String",
            "APP_UPDATE_URL",
            "https://today-mwo-meokji.vercel.app/api/releases/latest".asBuildConfigString()
        )
        buildConfigField("boolean", "APP_UPDATE_INSTALL_ENABLED", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = "today-what-to-eat"
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            isDebuggable = true
            matchingFallbacks += listOf("debug")
        }
        release {
            buildConfigField("boolean", "APP_UPDATE_INSTALL_ENABLED", "true")
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

val validateFoodAnalysisReleaseConfig = tasks.register("validateFoodAnalysisReleaseConfig") {
    inputs.property("foodAnalysisBaseUrl", foodAnalysisBaseUrl)
    doLast {
        val configuredUrl = inputs.properties["foodAnalysisBaseUrl"]?.toString().orEmpty()
        if (configuredUrl.isBlank()) return@doLast
        val valid = runCatching {
            val uri = URI(configuredUrl)
            val host = uri.host.orEmpty().lowercase().trim('[', ']')
            val parts = host.split('.')
            val octets = if (parts.size == 4) parts.map { it.toIntOrNull() } else emptyList()
            val privateIpv4 = octets.size == 4 && octets.all { it != null && it in 0..255 } &&
                (octets[0] == 0 || octets[0] == 10 || octets[0] == 127 ||
                    (octets[0] == 169 && octets[1] == 254) ||
                    (octets[0] == 172 && octets[1]!! in 16..31) ||
                    (octets[0] == 192 && octets[1] == 168))
            val localOrPrivate = host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host == "::1" || host.startsWith("fe80:") ||
                host.startsWith("fc") || host.startsWith("fd") || privateIpv4
            uri.scheme.equals("https", ignoreCase = true) &&
                host.isNotBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null &&
                !localOrPrivate
        }.getOrDefault(false)
        if (!valid) {
            throw GradleException(
                "Release 빌드에는 사용자 정보·쿼리·프래그먼트가 없고 로컬/사설 호스트가 아닌 " +
                    "유효한 HTTPS FOOD_ANALYSIS_BASE_URL이 필요합니다."
            )
        }
    }
}

val validateReleaseSigningConfig = tasks.register("validateReleaseSigningConfig") {
    inputs.property("releaseSigningConfigured", releaseSigningConfigured)
    doLast {
        val configured = inputs.properties["releaseSigningConfigured"] as? Boolean ?: false
        if (!configured) {
            throw GradleException("Release 서명 설정이 필요합니다. 사용자 Gradle properties를 확인하세요.")
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateFoodAnalysisReleaseConfig)
    dependsOn(validateReleaseSigningConfig)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.logging.interceptor)
    implementation(libs.material)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.play.services.location)
    implementation(libs.play.services.code.scanner)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    "qaImplementation"(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "ksp"(libs.androidx.room.compiler)
    "ksp"(libs.moshi.kotlin.codegen)
}
