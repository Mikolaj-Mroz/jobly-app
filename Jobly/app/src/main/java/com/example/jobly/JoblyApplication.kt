package com.example.jobly

import android.app.Application
import com.example.jobly.di.AppContainer
import com.example.jobly.di.DefaultAppContainer

class JoblyApplication : Application() {
    val appContainer: AppContainer by lazy { DefaultAppContainer() }
}
