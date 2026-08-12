package com.internal.tracker

import android.content.Context
import android.provider.Settings
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.internal.tracker.config.ConfigFileCodec
import com.internal.tracker.config.DeviceIdProvider
import com.internal.tracker.config.TlsMode
import com.internal.tracker.data.AppDatabase
import com.internal.tracker.network.ConnectionTester
import com.internal.tracker.network.OkHttpNetworkProbe
import com.internal.tracker.network.OsmAndClient
import com.internal.tracker.network.OsmAndRequestFactory
import com.internal.tracker.network.TlsClientFactory
import com.internal.tracker.profile.EncryptedProfileSecrets
import com.internal.tracker.profile.Profile
import com.internal.tracker.profile.ProfileRepository
import com.internal.tracker.queue.LocationQueueRepository
import com.internal.tracker.queue.LocationSample
import com.internal.tracker.tracking.TrackingController
import com.internal.tracker.tracking.TrackingPreferences
import com.internal.tracker.worker.QueueUploader
import okhttp3.OkHttpClient

@Suppress("DEPRECATION")
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "tracker.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
    val trackingPreferences = TrackingPreferences(appContext)
    val profiles = ProfileRepository(database.profileDao(), EncryptedProfileSecrets(appContext)) { trackingPreferences.enabled }
    val queue = LocationQueueRepository(database.pendingLocationDao())
    val configCodec = ConfigFileCodec()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        appContext,
        "device_identity",
        MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    val deviceId = DeviceIdProvider(
        { Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) },
        { encryptedPrefs.getString("fallback", null) },
        { encryptedPrefs.edit().putString("fallback", it).apply() },
    )

    private val tls = TlsClientFactory()
    private val requests = OsmAndRequestFactory()
    private val clientFor: (Profile) -> OkHttpClient = { profile ->
        when (profile.tlsMode) {
            TlsMode.SYSTEM -> tls.system()
            TlsMode.CUSTOM_CA -> tls.customCa(requireNotNull(profile.customCa) { "Chưa nhập Custom CA" })
            TlsMode.PINNING -> tls.pinning(profile.host, requireNotNull(profile.certificatePin))
        }
    }
    private val sender = OsmAndClient(clientFor, requests)
    val queueUploader = QueueUploader(queue, profiles::active, deviceId::get, sender)
    val connectionTester = ConnectionTester(OkHttpNetworkProbe(clientFor), sender)
    val trackingController = TrackingController(appContext, trackingPreferences)

    @Volatile
    var latestLocation: LocationSample? = null
}
