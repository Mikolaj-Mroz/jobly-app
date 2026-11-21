package com.example.jobly.data.remote

import android.os.Build
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkConfig {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun backendBaseOrigin(): String =
        if (isEmulator()) "http://10.0.2.2:8000" else "http://127.0.0.1:8000"

    fun offersBaseUrl(): String = "${backendBaseOrigin()}/offers/"
    fun notificationsBaseUrl(): String = "${backendBaseOrigin()}/notifications/"
    fun userBaseUrl(): String = "${backendBaseOrigin()}/user/"
    fun assistantBaseUrl(): String = "${backendBaseOrigin()}/assistant/"

    fun createOkHttpClient(
        readTimeoutSeconds: Long,
        connectTimeoutSeconds: Long = 15L
    ): OkHttpClient {
        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
            .addInterceptor(logger)
            .build()
    }

    fun createRetrofit(
        baseUrl: String,
        client: OkHttpClient,
        json: Json = NetworkConfig.json
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("google_sdk") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for") ||
            Build.HARDWARE.contains("goldfish") ||
            Build.HARDWARE.contains("ranchu") ||
            Build.PRODUCT.contains("sdk"))
    }
}
