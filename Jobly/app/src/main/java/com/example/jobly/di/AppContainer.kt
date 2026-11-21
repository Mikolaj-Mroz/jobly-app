package com.example.jobly.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.jobly.data.remote.NetworkConfig
import com.example.jobly.data.remote.api.AssistantApi
import com.example.jobly.data.remote.api.NotificationsApi
import com.example.jobly.data.remote.api.OffersApi
import com.example.jobly.data.remote.api.UserApi
import com.example.jobly.data.repository.AssistantRepository
import com.example.jobly.data.repository.NotificationsRepository
import com.example.jobly.data.repository.OffersRepository
import com.example.jobly.data.repository.RemoteAssistantRepository
import com.example.jobly.data.repository.RemoteNotificationsRepository
import com.example.jobly.data.repository.RemoteOffersRepository
import com.example.jobly.data.repository.RemoteUserRepository
import com.example.jobly.data.repository.UserRepository
import com.example.jobly.ui.assistant.AssistantViewModel
import com.example.jobly.ui.home.HomeViewModel
import com.example.jobly.ui.notifications.NotificationsViewModel
import com.example.jobly.ui.profile.ProfileViewModel
import com.example.jobly.ui.search.SearchViewModel

interface AppContainer {
    val offersRepository: OffersRepository
    val assistantRepository: AssistantRepository
    val notificationsRepository: NotificationsRepository
    val userRepository: UserRepository
    val viewModelFactory: ViewModelProvider.Factory
}

class DefaultAppContainer : AppContainer {

    private val defaultHttpClient = NetworkConfig.createOkHttpClient(readTimeoutSeconds = 30L)
    private val assistantHttpClient = NetworkConfig.createOkHttpClient(readTimeoutSeconds = 120L)

    // Separate Retrofit service definitions for all 4 JoblyBackend APIs
    private val offersApi: OffersApi by lazy {
        NetworkConfig.createRetrofit(NetworkConfig.offersBaseUrl(), defaultHttpClient)
            .create(OffersApi::class.java)
    }

    private val assistantApi: AssistantApi by lazy {
        NetworkConfig.createRetrofit(NetworkConfig.assistantBaseUrl(), assistantHttpClient)
            .create(AssistantApi::class.java)
    }

    private val notificationsApi: NotificationsApi by lazy {
        NetworkConfig.createRetrofit(NetworkConfig.notificationsBaseUrl(), defaultHttpClient)
            .create(NotificationsApi::class.java)
    }

    private val userApi: UserApi by lazy {
        NetworkConfig.createRetrofit(NetworkConfig.userBaseUrl(), defaultHttpClient)
            .create(UserApi::class.java)
    }

    override val userRepository: UserRepository by lazy {
        RemoteUserRepository(userApi)
    }

    override val offersRepository: OffersRepository by lazy {
        RemoteOffersRepository(offersApi, userRepository)
    }

    override val notificationsRepository: NotificationsRepository by lazy {
        RemoteNotificationsRepository(notificationsApi)
    }

    override val assistantRepository: AssistantRepository by lazy {
        RemoteAssistantRepository(assistantApi)
    }

    override val viewModelFactory: ViewModelProvider.Factory by lazy {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                        HomeViewModel(offersRepository, userRepository) as T
                    modelClass.isAssignableFrom(AssistantViewModel::class.java) ->
                        AssistantViewModel(assistantRepository, userRepository) as T
                    modelClass.isAssignableFrom(NotificationsViewModel::class.java) ->
                        NotificationsViewModel(notificationsRepository) as T
                    modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                        ProfileViewModel(userRepository) as T
                    modelClass.isAssignableFrom(SearchViewModel::class.java) ->
                        SearchViewModel(offersRepository) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
