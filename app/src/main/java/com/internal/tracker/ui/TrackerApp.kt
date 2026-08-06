package com.internal.tracker.ui

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.internal.tracker.AppContainer
import com.internal.tracker.TrackerApplication
import com.internal.tracker.config.ImportedProfile
import com.internal.tracker.config.Scheme
import com.internal.tracker.config.TlsMode
import com.internal.tracker.network.DiagnosticResult
import com.internal.tracker.network.TlsClientFactory
import com.internal.tracker.profile.Profile
import com.internal.tracker.tracking.PermissionAction
import com.internal.tracker.tracking.PermissionPolicy
import com.internal.tracker.tracking.PermissionSnapshot
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Destination { STATUS, PROFILES, DIAGNOSTICS }

@Composable
fun TrackerApp() {
    val container = (LocalContext.current.applicationContext as TrackerApplication).container
    var destination by rememberSaveable { mutableStateOf(Destination.STATUS) }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(destination == Destination.STATUS, { destination = Destination.STATUS }, { androidx.compose.material3.Icon(Icons.Default.Home, "Trạng thái") }, label = { Text("Trạng thái") })
                        NavigationBarItem(destination == Destination.PROFILES, { destination = Destination.PROFILES }, { androidx.compose.material3.Icon(Icons.Default.Settings, "Cấu hình") }, label = { Text("Cấu hình") })
                        NavigationBarItem(destination == Destination.DIAGNOSTICS, { destination = Destination.DIAGNOSTICS }, { androidx.compose.material3.Icon(Icons.Default.Build, "Chẩn đoán") }, label = { Text("Chẩn đoán") })
                    }
                },
            ) { padding ->
                when (destination) {
                    Destination.STATUS -> StatusScreen(container, Modifier.padding(padding))
                    Destination.PROFILES -> ProfilesScreen(container, Modifier.padding(padding))
                    Destination.DIAGNOSTICS -> DiagnosticsScreen(container, Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(container: AppContainer, modifier: Modifier) {
    val context = LocalContext.current
    val activity = context as Activity
    val profiles by container.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    val queued by container.queue.count().collectAsStateWithLifecycle(0)
    var refresh by remember { mutableIntStateOf(0) }
    var fineRequested by rememberSaveable { mutableStateOf(false) }
    val fineLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    RefreshOnResume { refresh++ }
    val permission = permissionSnapshot(activity, fineRequested, refresh)
    val action = PermissionPolicy.next(permission)
    val active = profiles.firstOrNull { it.active }
    val tracking = container.trackingController.isTracking()

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Theo dõi nội bộ", style = MaterialTheme.typography.headlineSmall)
        StatusRow("Trạng thái", if (tracking) "Đang theo dõi" else "Đã dừng")
        StatusRow("Profile", active?.name ?: "Chưa chọn")
        StatusRow("Device ID", container.deviceId.get())
        OutlinedButton(
            onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Device ID", container.deviceId.get()))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Sao chép Device ID") }
        StatusRow("Điểm chờ gửi", queued.toString())
        StatusRow("GPS cuối", formatTime(container.trackingPreferences.lastLocationTime))
        StatusRow("Gửi cuối", formatTime(container.trackingPreferences.lastSendTime))
        if (tracking) {
            Button(onClick = { container.trackingController.stop(); refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Dừng theo dõi") }
        } else {
            Button(
                onClick = {
                    when (action) {
                        PermissionAction.OpenLocationSettings -> context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        PermissionAction.RequestFine -> { fineRequested = true; fineLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) }
                        PermissionAction.RequestBackground -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        PermissionAction.RequestNotifications -> notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        PermissionAction.OpenAppSettings -> openAppSettings(activity)
                        PermissionAction.Ready -> if (active != null) container.trackingController.start()
                    }
                    refresh++
                },
                enabled = active != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (active == null) "Chọn profile trước" else permissionButtonText(action)) }
        }
        BatteryButton()
        if (active?.scheme == Scheme.HTTP) Text("Cảnh báo: HTTP không mã hóa. Chỉ dùng trong mạng nội bộ/VPN.", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ProfilesScreen(container: AppContainer, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by container.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    var name by rememberSaveable { mutableStateOf("Production") }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("443") }
    var interval by rememberSaveable { mutableStateOf("60") }
    var scheme by rememberSaveable { mutableStateOf(Scheme.HTTPS) }
    var tlsMode by rememberSaveable { mutableStateOf(TlsMode.SYSTEM) }
    var pin by rememberSaveable { mutableStateOf("") }
    var customCa by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<ImportedProfile?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(container.configCodec.encodeTemplate()) } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val text = context.contentResolver.openInputStream(it)?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            container.configCodec.decode(text).onSuccess { profile -> preview = profile }.onFailure { problem -> error = problem.message }
        }
    }
    val caLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        customCa = uri?.let { context.contentResolver.openInputStream(it)?.use { input -> input.readBytes() } }
    }

    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Cấu hình kết nối", style = MaterialTheme.typography.headlineSmall)
        Text("Device ID: ${container.deviceId.get()}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ exportLauncher.launch("traccar-profile.json") }) { Text("Tải file mẫu") }
            OutlinedButton({ importLauncher.launch(arrayOf("application/json", "text/plain")) }) { Text("Nhập file") }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Tên profile") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(host, { host = it }, label = { Text("Server host/IP") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(scheme == Scheme.HTTP, { scheme = Scheme.HTTP; tlsMode = TlsMode.SYSTEM }, { Text("HTTP") })
            FilterChip(scheme == Scheme.HTTPS, { scheme = Scheme.HTTPS }, { Text("HTTPS") })
        }
        OutlinedTextField(port, { port = it.filter(Char::isDigit) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(interval, { interval = it.filter(Char::isDigit) }, label = { Text("Chu kỳ gửi (giây)") }, modifier = Modifier.fillMaxWidth())
        if (scheme == Scheme.HTTPS) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(tlsMode == TlsMode.SYSTEM, { tlsMode = TlsMode.SYSTEM }, { Text("System CA") })
                FilterChip(tlsMode == TlsMode.CUSTOM_CA, { tlsMode = TlsMode.CUSTOM_CA }, { Text("Custom CA") })
                FilterChip(tlsMode == TlsMode.PINNING, { tlsMode = TlsMode.PINNING }, { Text("Pinning") })
            }
            if (tlsMode == TlsMode.CUSTOM_CA) OutlinedButton({ caLauncher.launch(arrayOf("application/x-x509-ca-cert", "application/pkix-cert", "*/*")) }) { Text(if (customCa == null) "Chọn file .crt" else "Đã chọn certificate") }
            if (tlsMode == TlsMode.PINNING) OutlinedTextField(pin, { pin = it }, label = { Text("SHA-256 pin") }, modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val json = buildConfigJson(name, host, port, scheme, interval, tlsMode, pin)
                container.configCodec.decode(json).onSuccess { profile ->
                    val caError = if (profile.tlsMode == TlsMode.CUSTOM_CA) runCatching {
                        TlsClientFactory().customCa(requireNotNull(customCa))
                    }.exceptionOrNull() else null
                    if (caError != null) error = "File chứng chỉ X.509 không hợp lệ" else scope.launch {
                        val id = container.profiles.save(profile, customCa)
                        if (profiles.none { it.active }) container.profiles.activate(id)
                        error = null
                    }
                }.onFailure { error = it.message }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Lưu profile") }

        Spacer(Modifier.height(8.dp))
        profiles.forEach { profile ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text(profile.name); Text("${profile.scheme.name.lowercase()}://${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall) }
                Row {
                    TextButton({ scope.launch { container.profiles.activate(profile.id) } }, enabled = !profile.active && !container.trackingController.isTracking()) { Text(if (profile.active) "Đang dùng" else "Dùng") }
                    TextButton({ scope.launch { container.profiles.delete(profile.id) } }, enabled = !profile.active) { Text("Xóa") }
                }
            }
        }
    }

    preview?.let { imported ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text("Xem trước cấu hình") },
            text = { Text("${imported.name}\n${imported.scheme.name.lowercase()}://${imported.host}:${imported.port}\nChu kỳ: ${imported.intervalSeconds} giây\nTLS: ${imported.tlsMode}") },
            confirmButton = { TextButton({ name = imported.name; host = imported.host; port = imported.port.toString(); scheme = imported.scheme; interval = imported.intervalSeconds.toString(); tlsMode = imported.tlsMode; pin = imported.certificatePin.orEmpty(); preview = null }) { Text("Áp dụng vào form") } },
            dismissButton = { TextButton({ preview = null }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun DiagnosticsScreen(container: AppContainer, modifier: Modifier) {
    val profiles by container.profiles.observeAll().collectAsStateWithLifecycle(emptyList())
    val active = profiles.firstOrNull { it.active }
    val scope = rememberCoroutineScope()
    var networkResult by remember { mutableStateOf<DiagnosticResult?>(null) }
    var dataResult by remember { mutableStateOf<DiagnosticResult?>(null) }
    Column(modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Chẩn đoán kết nối", style = MaterialTheme.typography.headlineSmall)
        Text(active?.name ?: "Chưa có profile active")
        Button(
            onClick = { active?.let { scope.launch { networkResult = withContext(Dispatchers.IO) { container.connectionTester.testNetwork(it) } } } },
            enabled = active != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bước 1: Kiểm tra máy chủ") }
        networkResult?.let { Text(diagnosticText(it)) }
        Button(
            onClick = { active?.let { scope.launch { dataResult = withContext(Dispatchers.IO) { container.connectionTester.sendLatest(it, container.deviceId.get(), container.latestLocation) } } } },
            enabled = active != null && networkResult == DiagnosticResult.ServerReachable,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Bước 2: Gửi GPS thật gần nhất") }
        dataResult?.let { Text(diagnosticText(it)) }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) }
}

@Composable
private fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) onResume() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun BatteryButton() {
    val context = LocalContext.current
    val manager = context.getSystemService(PowerManager::class.java)
    if (!manager.isIgnoringBatteryOptimizations(context.packageName)) {
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Cho phép chạy ổn định khi khóa màn hình") }
    }
}

private fun permissionSnapshot(activity: Activity, fineRequested: Boolean, refresh: Int): PermissionSnapshot {
    refresh.hashCode()
    val location = activity.getSystemService(LocationManager::class.java).isLocationEnabled
    val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return PermissionSnapshot(
        location,
        fine,
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED,
        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        fineRequested && !fine && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION),
    )
}

private fun permissionButtonText(action: PermissionAction) = when (action) {
    PermissionAction.Ready -> "Bắt đầu theo dõi"
    PermissionAction.OpenLocationSettings -> "Bật dịch vụ vị trí"
    PermissionAction.RequestFine -> "Cấp quyền vị trí"
    PermissionAction.RequestBackground -> "Cho phép vị trí nền"
    PermissionAction.RequestNotifications -> "Cho phép thông báo"
    PermissionAction.OpenAppSettings -> "Mở cài đặt ứng dụng"
}

private fun openAppSettings(activity: Activity) = activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
private fun formatTime(value: Long) = if (value == 0L) "Chưa có" else DateFormat.getDateTimeInstance().format(Date(value))

private fun diagnosticText(result: DiagnosticResult) = when (result) {
    DiagnosticResult.ServerReachable -> "Máy chủ đã phản hồi"
    DiagnosticResult.DataAccepted -> "Máy chủ đã nhận dữ liệu GPS"
    DiagnosticResult.RealLocationRequired -> "Chưa có GPS thật. Hãy bắt đầu theo dõi và thử lại."
    DiagnosticResult.DnsError -> "Không tìm thấy tên máy chủ (DNS)"
    DiagnosticResult.ConnectionRefused -> "Máy chủ từ chối kết nối"
    DiagnosticResult.Timeout -> "Kết nối quá thời gian chờ"
    DiagnosticResult.TlsError -> "Chứng chỉ HTTPS không hợp lệ hoặc không khớp"
    is DiagnosticResult.HttpError -> "Máy chủ trả lỗi HTTP ${result.code}"
    is DiagnosticResult.NetworkError -> "Lỗi mạng: ${result.detail}"
}

private fun buildConfigJson(name: String, host: String, port: String, scheme: Scheme, interval: String, tls: TlsMode, pin: String): String = org.json.JSONObject()
    .put("version", 1)
    .put("name", name)
    .put("host", host)
    .put("port", port.toIntOrNull() ?: 0)
    .put("scheme", scheme.name.lowercase())
    .put("intervalSeconds", interval.toIntOrNull() ?: 0)
    .put("tlsMode", when (tls) { TlsMode.SYSTEM -> "system"; TlsMode.CUSTOM_CA -> "customCa"; TlsMode.PINNING -> "pinning" })
    .apply { if (pin.isNotBlank()) put("certificatePin", pin) }
    .toString()
