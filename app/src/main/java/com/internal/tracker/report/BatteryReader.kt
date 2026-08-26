package com.internal.tracker.report

import android.content.Context
import android.os.BatteryManager

class BatteryReader(context: Context) {
    private val manager = context.getSystemService(BatteryManager::class.java)
    fun percent(): Int? = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
}
