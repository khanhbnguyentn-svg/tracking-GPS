package com.internal.tracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.internal.tracker.ui.TrackerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as TrackerApplication).container.reconcileAppLaunch()
        setContent { TrackerApp() }
    }
}
