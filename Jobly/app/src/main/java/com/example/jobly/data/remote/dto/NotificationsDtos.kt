package com.example.jobly.data.remote.dto

import com.example.jobly.model.DailyDigestPushResult
import com.example.jobly.model.DailyDigestStatus
import com.example.jobly.model.NotificationItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val heading: String,
    val offer: JobOfferDto,
    @SerialName("is_read") val isRead: Boolean = false
)

@Serializable
data class NotificationsResponseDto(
    val notifications: List<NotificationDto> = emptyList()
)

@Serializable
data class DailyDigestResultDto(
    val pushed: Boolean = false,
    val message: String = "",
    val notification: NotificationDto? = null
)

@Serializable
data class DailyDigestStatusDto(
    @SerialName("scheduler_enabled") val schedulerEnabled: Boolean = true,
    @SerialName("daily_enabled") val dailyEnabled: Boolean = true,
    @SerialName("interval_hours") val intervalHours: Int = 24,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("last_offer_id") val lastOfferId: String? = null
)

fun NotificationDto.toDomain(): NotificationItem = NotificationItem(
    id = id,
    heading = heading,
    offer = offer.toDomain(),
    isRead = isRead
)

fun DailyDigestResultDto.toDomain(): DailyDigestPushResult = DailyDigestPushResult(
    pushed = pushed,
    message = message,
    notification = notification?.toDomain()
)

fun DailyDigestStatusDto.toDomain(): DailyDigestStatus = DailyDigestStatus(
    schedulerEnabled = schedulerEnabled,
    dailyEnabled = dailyEnabled,
    intervalHours = intervalHours,
    nextRunAt = nextRunAt,
    lastRunAt = lastRunAt,
    lastOfferId = lastOfferId
)
