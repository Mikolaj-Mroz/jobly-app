package com.example.jobly.data.repository

import com.example.jobly.data.remote.api.NotificationsApi
import com.example.jobly.data.remote.dto.toDomain
import com.example.jobly.model.DailyDigestPushResult
import com.example.jobly.model.DailyDigestStatus
import com.example.jobly.model.NotificationItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

interface NotificationsRepository {
    val notifications: StateFlow<List<NotificationItem>>
    val unreadCount: StateFlow<Int>

    suspend fun refreshNotifications(): Result<Unit>
    suspend fun markAsRead(notificationId: String): Result<Unit>
    suspend fun triggerDailyDigest(force: Boolean = true): Result<DailyDigestPushResult>
    suspend fun getDailyDigestStatus(): Result<DailyDigestStatus>
}

class MockNotificationsRepository : NotificationsRepository {
    private val _notifications = MutableStateFlow(SampleSeedData.sampleNotifications)
    override val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(SampleSeedData.sampleNotifications.count { !it.isRead })
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun refreshNotifications(): Result<Unit> = Result.success(Unit)

    override suspend fun markAsRead(notificationId: String): Result<Unit> {
        val current = _notifications.value
        val index = current.indexOfFirst { it.id == notificationId }
        if (index >= 0 && !current[index].isRead) {
            val updated = current.toMutableList().apply {
                this[index] = current[index].copy(isRead = true)
            }
            _notifications.value = updated
            _unreadCount.value = updated.count { !it.isRead }
        }
        return Result.success(Unit)
    }

    override suspend fun triggerDailyDigest(force: Boolean): Result<DailyDigestPushResult> {
        val sampleOffer = SampleSeedData.sampleBestOffers.first()
        val item = NotificationItem(
            id = "daily_${System.currentTimeMillis()}",
            heading = "Daily best match (92% match)",
            offer = sampleOffer,
            isRead = false
        )
        _notifications.update { listOf(item) + it }
        _unreadCount.value = _notifications.value.count { !it.isRead }
        return Result.success(
            DailyDigestPushResult(
                pushed = true,
                message = "Pushed daily best match: ${sampleOffer.title}",
                notification = item
            )
        )
    }

    override suspend fun getDailyDigestStatus(): Result<DailyDigestStatus> =
        Result.success(DailyDigestStatus(schedulerEnabled = true, dailyEnabled = true, intervalHours = 24))
}

class RemoteNotificationsRepository(
    private val api: NotificationsApi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : NotificationsRepository {
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    override val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun refreshNotifications(): Result<Unit> = suspendResultOf {
        withContext(ioDispatcher) {
            val loaded = api.getNotifications().notifications.map { it.toDomain() }
            _notifications.value = loaded
            _unreadCount.value = loaded.count { !it.isRead }
        }
    }

    override suspend fun markAsRead(notificationId: String): Result<Unit> = suspendResultOf {
        withContext(ioDispatcher) {
            api.markAsRead(notificationId)
            val updated = _notifications.value.map { item ->
                if (item.id == notificationId) item.copy(isRead = true) else item
            }
            _notifications.value = updated
            _unreadCount.value = updated.count { !it.isRead }
        }
    }

    override suspend fun triggerDailyDigest(force: Boolean): Result<DailyDigestPushResult> =
        suspendResultOf {
            withContext(ioDispatcher) {
                val result = api.triggerDailyDigest(force).toDomain()
                result.notification?.let { newItem ->
                    _notifications.update { existing ->
                        listOf(newItem) + existing.filterNot { it.id == newItem.id }
                    }
                    _unreadCount.value = _notifications.value.count { !it.isRead }
                }
                result
            }
        }

    override suspend fun getDailyDigestStatus(): Result<DailyDigestStatus> = suspendResultOf {
        withContext(ioDispatcher) {
            api.getDailyDigestStatus().toDomain()
        }
    }
}
