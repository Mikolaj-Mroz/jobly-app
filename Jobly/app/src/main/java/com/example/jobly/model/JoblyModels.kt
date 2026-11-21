package com.example.jobly.model

enum class CardAccent {
    YELLOW,
    YELLOW_ALT,
    BLUE,
    PINK,
    GREEN
}

data class JobOffer(
    val id: String,
    val initial: String,
    val title: String,
    val salaryRange: String,
    val salaryUnit: String,
    val skills: List<String>,
    val companyName: String,
    val level: String,
    val contractType: String,
    val workSchedule: String,
    val accent: CardAccent,
    val description: String,
    val externalUrl: String? = null,
    val matchScore: Int? = null,
    val matchReasons: List<String> = emptyList()
)

data class CvItem(
    val id: String,
    val fileName: String,
    val updatedLabel: String,
    val pageCount: Int? = null,
    val fileSizeBytes: Int? = null
)

data class NotificationItem(
    val id: String,
    val heading: String,
    val offer: JobOffer,
    val isRead: Boolean = false
)

data class UserProfile(
    val name: String,
    val phone: String,
    val email: String
)

data class UserSettings(
    val jobAlertsEnabled: Boolean = true,
    val dailyDigestEnabled: Boolean = true,
    val radiusKm: Double = 25.0,
    val currency: String = "PLN",
    val city: String = "Krakow",
    val latitude: Double = 50.0647,
    val longitude: Double = 19.9450,
    val onboardingCompleted: Boolean = false
)

data class DailyDigestStatus(
    val schedulerEnabled: Boolean = true,
    val dailyEnabled: Boolean = true,
    val intervalHours: Int = 24,
    val nextRunAt: String? = null,
    val lastRunAt: String? = null,
    val lastOfferId: String? = null
)

data class DailyDigestPushResult(
    val pushed: Boolean,
    val message: String,
    val notification: NotificationItem? = null
)

data class FeedbackReceipt(
    val id: String,
    val category: String,
    val message: String,
    val status: String = "received"
)

data class ChatMessage(
    val id: String,
    val text: String,
    val isUser: Boolean,
    val isError: Boolean = false,
    val updatedCv: CvItem? = null,
    val suggestedOffers: List<JobOffer> = emptyList()
)
