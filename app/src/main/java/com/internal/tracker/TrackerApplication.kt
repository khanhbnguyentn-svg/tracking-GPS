package com.internal.tracker

import android.app.Application
import com.internal.tracker.core.platform.SetPlatformFactory
import com.internal.tracker.core.platform.SetPlatformModule

class TrackerApplication : Application() {
    val platform: SetPlatformModule by lazy { SetPlatformFactory.create(this) }

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
    }
}
