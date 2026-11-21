package com.example.jobly.data.remote.dto

import com.example.jobly.model.CvItem
import com.example.jobly.model.FeedbackReceipt
import com.example.jobly.model.UserProfile
import com.example.jobly.model.UserSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val name: String,
    val phone: String,
    val email: String
)

@Serializable
data class UpdateProfileRequestDto(
    val name: String,
    val phone: String,
    val email: String
)

@Serializable
data class UserSettingsDto(
    @SerialName("job_alerts_enabled") val jobAlertsEnabled: Boolean = true,
    @SerialName("daily_digest_enabled") val dailyDigestEnabled: Boolean = true,
    @SerialName("radius_km") val radiusKm: Double = 25.0,
    val currency: String = "PLN",
    val city: String = "Krakow",
    val latitude: Double = 50.0647,
    val longitude: Double = 19.9450,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean = false
)

@Serializable
data class UpdateUserSettingsRequestDto(
    @SerialName("job_alerts_enabled") val jobAlertsEnabled: Boolean? = null,
    @SerialName("daily_digest_enabled") val dailyDigestEnabled: Boolean? = null,
    @SerialName("radius_km") val radiusKm: Double? = null,
    val currency: String? = null,
    val city: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("onboarding_completed") val onboardingCompleted: Boolean? = null
)

@Serializable
data class FeedbackRequestDto(
    val category: String = "General",
    val message: String,
    @SerialName("contact_email") val contactEmail: String? = null
)

@Serializable
data class FeedbackResponseDto(
    val id: String,
    val category: String,
    val message: String,
    val status: String = "received"
)

@Serializable
data class CvDto(
    val id: String,
    @SerialName("file_name") val fileName: String,
    @SerialName("updated_label") val updatedLabel: String = "Updated today",
    @SerialName("page_count") val pageCount: Int? = null,
    @SerialName("file_size_bytes") val fileSizeBytes: Int? = null
)

@Serializable
data class CvsResponseDto(
    val cvs: List<CvDto> = emptyList()
)

@Serializable
data class CreateCvRequestDto(
    @SerialName("file_name") val fileName: String
)

@Serializable
data class ApplicationsResponseDto(
    val applications: List<JobOfferDto> = emptyList()
)

fun UserProfileDto.toDomain(): UserProfile = UserProfile(
    name = name,
    phone = phone,
    email = email
)

fun UserSettingsDto.toDomain(): UserSettings = UserSettings(
    jobAlertsEnabled = jobAlertsEnabled,
    dailyDigestEnabled = dailyDigestEnabled,
    radiusKm = radiusKm,
    currency = currency,
    city = city,
    latitude = latitude,
    longitude = longitude,
    onboardingCompleted = onboardingCompleted
)

fun FeedbackResponseDto.toDomain(): FeedbackReceipt = FeedbackReceipt(
    id = id,
    category = category,
    message = message,
    status = status
)

fun CvDto.toDomain(): CvItem = CvItem(
    id = id,
    fileName = fileName,
    updatedLabel = updatedLabel,
    pageCount = pageCount,
    fileSizeBytes = fileSizeBytes
)
