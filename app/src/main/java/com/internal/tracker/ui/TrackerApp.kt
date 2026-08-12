package com.internal.tracker.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.LocalActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.internal.tracker.AppContainer
import com.internal.tracker.TrackerApplication
import com.internal.tracker.config.PilotConfig
import com.internal.tracker.mail.MailResult
import com.internal.tracker.tracking.PermissionAction
import com.internal.tracker.tracking.PermissionPolicy
import com.internal.tracker.tracking.PermissionSnapshot
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TrackerApp() {
    val container = (LocalContext.current.applicationContext as TrackerApplication).container
    var unlocked by remember { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(Destination.STATUS) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!unlocked) PinScreen(container) { unlocked = true } else Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(destination == Destination.STATUS, { destination = Destination.STATUS }, { Icon(Icons.Default.Home, "Trang thai") }, label = { Text("Trang thai") })
                        NavigationBarItem(destination == Destination.SETTINGS, { destination = Destination.SETTINGS }, { Icon(Icons.Default.Settings, "Cau hinh") }, label = { Text("Cau hinh") })
                        NavigationBarItem(destination == Destination.HISTORY, { destination = Destination.HISTORY }, { Icon(Icons.Default.History, "Lich su") }, label = { Text("Lich su") })
                    }
                },
            ) { padding ->
                when (destination) {
                    Destination.STATUS -> StatusScreen(container, Modifier.padding(padding))
                    Destination.SETTINGS -> AdminSettingsScreen(container, Modifier.padding(padding))
                    Destination.HISTORY -> HistoryScreen(container, Modifier.padding(padding))
                    Destination.PIN -> Unit
                }
            }
        }
    }
}

@Composable
private fun PinScreen(container: AppContainer, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mo khoa ung dung", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(8); error = false }, label = { Text("PIN quan tri") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), isError = error, modifier = Modifier.fillMaxWidth())
        Button({ if (container.adminPin.verify(pin)) onUnlocked() else error = true }, modifier = Modifier.fillMaxWidth()) { Text("Mo khoa") }
        if (error) Text("PIN khong dung", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun StatusScreen(container: AppContainer, modifier: Modifier) {
    val activity = LocalActivity.current ?: return
    var refresh by remember { mutableIntStateOf(0) }
    var fineRequested by remember { mutableStateOf(false) }
    val fine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val snapshot = permissionSnapshot(activity, fineRequested, refresh)
    val action = PermissionPolicy.next(snapshot)
    val tracking = container.trackingPreferences.enabled
    val configReady = container.pilotConfig.load().isValid()
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theo doi GPS", style = MaterialTheme.typography.headlineSmall)
        StatusRow("Trang thai", if (tracking) "Dang theo doi" else "Da dung")
        StatusRow("Thiet bi", container.pilotConfig.load().deviceNumber)
        StatusRow("Device ID", container.deviceId.get())
        StatusRow("GPS cuoi", formatTime(container.trackingPreferences.lastLocationTime))
        StatusRow("Email cuoi", formatTime(container.trackingPreferences.lastSendTime))
        StatusRow("Ky gui du kien", formatTime(container.trackingPreferences.nextRunTime))
        container.trackingPreferences.lastError?.let { Text("Loi gan nhat: $it", color = MaterialTheme.colorScheme.error) }
        if (action != PermissionAction.Ready) OutlinedButton({
            when (action) {
                PermissionAction.OpenLocationSettings -> activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                PermissionAction.RequestFine -> { fineRequested = true; fine.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                PermissionAction.RequestBackground -> if (Build.VERSION.SDK_INT >= 30) openAppSettings(activity) else background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                PermissionAction.RequestNotifications -> if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                PermissionAction.OpenAppSettings -> openAppSettings(activity)
                PermissionAction.Ready -> Unit
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Cap quyen thiet bi") }
        Button({
            if (tracking) {
                container.trackingPreferences.enabled = false
            } else if (action == PermissionAction.Ready && configReady) {
                container.trackingPreferences.startedAt = System.currentTimeMillis()
                container.trackingPreferences.enabled = true
            }
            container.reconcileSchedule()
            refresh++
        }, enabled = tracking || (action == PermissionAction.Ready && configReady), modifier = Modifier.fillMaxWidth()) {
            Text(if (tracking) "Dung theo doi" else "Bat dau theo doi")
        }
        if (!configReady) Text("Can hoan tat cau hinh truoc khi bat dau.", color = MaterialTheme.colorScheme.error)
        Text("Android co the tri hoan tac vu nen; gio gui la khoang du kien.")
    }
}

@Composable
private fun AdminSettingsScreen(container: AppContainer, modifier: Modifier) {
    val current = remember { container.pilotConfig.load() }
    var device by remember { mutableStateOf(current.deviceNumber) }
    var recipient by remember { mutableStateOf(current.recipient) }
    var interval by remember { mutableIntStateOf(current.intervalHours) }
    var sender by remember { mutableStateOf(current.sender) }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cau hinh quan tri", style = MaterialTheme.typography.headlineSmall)
        Text("Device ID: ${container.deviceId.get()}")
        OutlinedTextField(device, { device = it.filter(Char::isDigit).take(3) }, label = { Text("So thiet bi 001-100") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(recipient, { recipient = it }, label = { Text("Email nhan") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(6, 12, 24).forEach { hours -> FilterChip(interval == hours, { interval = hours }, { Text("${hours}h") }) } }
        OutlinedTextField(sender, { sender = it }, label = { Text("Gmail gui") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("App Password moi (de trong neu giu nguyen)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({
            scope.launch {
                val candidate = PilotConfig(device, recipient, interval, sender, password.ifBlank { container.pilotConfig.load().appPassword }).normalized()
                if (!candidate.isValid()) result = "Cau hinh khong hop le" else {
                    val tested = withContext(Dispatchers.IO) { container.gmail.testCredentials(candidate) }
                    result = if (tested == MailResult.Accepted) {
                        container.pilotConfig.save(candidate).getOrThrow()
                        container.reconcileSchedule()
                        password = ""
                        "Da luu va dang nhap Gmail thanh cong"
                    } else "Khong the dang nhap Gmail: ${tested.javaClass.simpleName}"
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Luu va kiem tra") }
        result?.let { Text(it) }
        Text("Doi PIN", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN hien tai") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN moi") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedButton({ result = container.adminPin.change(currentPin, newPin).fold({ "Da doi PIN" }, { it.message ?: "Khong the doi PIN" }) }, modifier = Modifier.fillMaxWidth()) { Text("Doi PIN") }
    }
}

@Composable
private fun HistoryScreen(container: AppContainer, modifier: Modifier) {
    val records by container.history.observeBetween(0, Long.MAX_VALUE).collectAsStateWithLifecycle(emptyList())
    val context = LocalContext.current
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Lich su du lieu", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton({
            val file = container.csv.writeCompleteExport(container.pilotConfig.load().deviceNumber, records)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, container.csv.shareUri(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Chia se lich su"))
        }, enabled = records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Xuat toan bo CSV") }
        records.forEach { record ->
            Column(Modifier.fillMaxWidth()) {
                Text("#${record.id} - ${formatTime(record.capturedAt)} - ${record.state.name}")
                Text("Pin ${record.batteryPercent ?: "?"}% | Sai so ${record.accuracy ?: "?"} m")
                Text("Da theo doi ${formatDuration(record.trackedDurationMillis)}")
                Text("${record.latitude}, ${record.longitude}")
            }
        }
    }
}

@Composable private fun StatusRow(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }

private fun permissionSnapshot(activity: Activity, fineRequested: Boolean, refresh: Int): PermissionSnapshot {
    refresh.hashCode()
    val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return PermissionSnapshot(
        activity.getSystemService(LocationManager::class.java).isLocationEnabled,
        fine,
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED,
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fineRequested && !fine && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
    )
}

private fun openAppSettings(activity: Activity) = activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
private fun formatTime(value: Long) = if (value == 0L) "Chua co" else DateFormat.getDateTimeInstance().format(Date(value))
private fun formatDuration(value: Long): String {
    val minutes = value.coerceAtLeast(0) / 60_000
    return "${minutes / 1_440}d ${minutes / 60 % 24}h ${minutes % 60}m"
}
