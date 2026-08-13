package com.internal.tracker.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import java.time.Instant
import java.time.Year
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PinRequest(
    val action: ProtectedAction,
    val onVerified: () -> Unit,
)

private data class DeleteRequest(
    val action: ProtectedAction,
    val label: String,
    val range: HistoryTimeRange? = null,
)

@Composable
fun TrackerApp() {
    val container = (LocalContext.current.applicationContext as TrackerApplication).container
    var unlocked by remember { mutableStateOf(false) }
    var settingsUnlocked by rememberSaveable { mutableStateOf(false) }
    var destination by rememberSaveable { mutableStateOf(Destination.STATUS) }
    var pinRequest by remember { mutableStateOf<PinRequest?>(null) }

    fun requirePin(action: ProtectedAction, onVerified: () -> Unit) {
        pinRequest = PinRequest(action, onVerified)
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            if (!unlocked) {
                PinScreen(container) { unlocked = true }
            } else {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                destination == Destination.STATUS,
                                { destination = Destination.STATUS },
                                { Icon(Icons.Default.Home, "Trạng thái") },
                                label = { Text("Trạng thái") },
                            )
                            NavigationBarItem(
                                destination == Destination.SETTINGS,
                                {
                                    if (AppUiPolicy.requiresPin(
                                            ProtectedAction.OPEN_SETTINGS,
                                            settingsUnlocked,
                                        )
                                    ) {
                                        requirePin(ProtectedAction.OPEN_SETTINGS) {
                                            settingsUnlocked = true
                                            destination = Destination.SETTINGS
                                        }
                                    } else {
                                        destination = Destination.SETTINGS
                                    }
                                },
                                { Icon(Icons.Default.Settings, "Cấu hình") },
                                label = { Text("Cấu hình") },
                            )
                            NavigationBarItem(
                                destination == Destination.HISTORY,
                                { destination = Destination.HISTORY },
                                { Icon(Icons.Default.History, "Lịch sử") },
                                label = { Text("Lịch sử") },
                            )
                        }
                    },
                ) { padding ->
                    when (destination) {
                        Destination.STATUS -> StatusScreen(
                            container,
                            Modifier.padding(padding),
                            ::requirePin,
                        )
                        Destination.SETTINGS -> AdminSettingsScreen(container, Modifier.padding(padding))
                        Destination.HISTORY -> HistoryScreen(
                            container,
                            Modifier.padding(padding),
                            ::requirePin,
                        )
                        Destination.PIN -> Unit
                    }
                }
            }
        }

        pinRequest?.let { request ->
            PinVerificationDialog(
                container = container,
                action = request.action,
                onDismiss = { pinRequest = null },
                onVerified = {
                    pinRequest = null
                    request.onVerified()
                },
            )
        }
    }
}

@Composable
private fun PinScreen(container: AppContainer, onUnlocked: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mở khóa ứng dụng", style = MaterialTheme.typography.headlineSmall)
        PinField(pin, { pin = it; error = false }, error)
        Button(
            { if (container.adminPin.verify(pin)) onUnlocked() else error = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Mở khóa") }
        if (error) Text("PIN không đúng", color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun PinVerificationDialog(
    container: AppContainer,
    action: ProtectedAction,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
) {
    var pin by remember(action) { mutableStateOf("") }
    var error by remember(action) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pinDialogTitle(action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it; error = false }, error)
                if (error) Text("PIN không đúng", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button({
                if (container.adminPin.verify(pin)) onVerified() else error = true
            }) { Text("Xác nhận") }
        },
        dismissButton = { OutlinedButton(onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun PinField(value: String, onValueChange: (String) -> Unit, isError: Boolean) {
    OutlinedTextField(
        value,
        { onValueChange(it.filter(Char::isDigit).take(8)) },
        label = { Text("PIN quản trị") },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        isError = isError,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StatusScreen(
    container: AppContainer,
    modifier: Modifier,
    requirePin: (ProtectedAction, () -> Unit) -> Unit,
) {
    val activity = LocalActivity.current ?: return
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var fineRequested by remember { mutableStateOf(false) }
    val fine = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        container.reconcileTracking()
        refresh++
    }
    val background = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.reconcileTracking()
        refresh++
    }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.reconcileTracking()
        refresh++
    }
    val activityRecognition = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        container.reconcileTracking()
        refresh++
    }
    val snapshot = permissionSnapshot(activity, fineRequested, refresh)
    val action = PermissionPolicy.next(snapshot)
    val tracking = container.trackingPreferences.enabled
    val config = container.pilotConfig.load()
    val configReady = config.isValid()
    val status = StatusUiModel.create(
        tracking = tracking,
        deviceNumber = config.deviceNumber,
        lastLocationTime = container.trackingPreferences.lastLocationTime,
        lastSendTime = container.trackingPreferences.lastSendTime,
        nextRunTime = container.trackingPreferences.nextRunTime,
        formatTime = ::formatTime,
    )
    val activityPermissionMissing = ContextCompat.checkSelfPermission(
        activity,
        Manifest.permission.ACTIVITY_RECOGNITION,
    ) !=
        PackageManager.PERMISSION_GRANTED

    Column(
        modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Theo dõi GPS", style = MaterialTheme.typography.headlineSmall)
        status.rows.forEach { StatusRow(it.label, it.value) }
        container.trackingPreferences.lastError?.let {
            Text("Lỗi gần nhất: $it", color = MaterialTheme.colorScheme.error)
        }
        if (action != PermissionAction.Ready) {
            OutlinedButton({
                when (action) {
                    PermissionAction.OpenLocationSettings -> activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    PermissionAction.RequestFine -> {
                        fineRequested = true
                        fine.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                    PermissionAction.RequestBackground -> if (Build.VERSION.SDK_INT >= 30) {
                        openAppSettings(activity)
                    } else {
                        background.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    PermissionAction.RequestNotifications -> if (Build.VERSION.SDK_INT >= 33) {
                        notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    PermissionAction.OpenAppSettings -> openAppSettings(activity)
                    PermissionAction.Ready -> Unit
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Cấp quyền thiết bị") }
        }
        if (action == PermissionAction.Ready && activityPermissionMissing) {
            Text("Nhận diện hoạt động giúp nhận biết xe bắt đầu chạy. Nếu không cấp, app vẫn theo dõi bằng GPS.")
            OutlinedButton(
                { activityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cấp quyền nhận diện hoạt động") }
        }
        Button({
            if (tracking) {
                requirePin(ProtectedAction.STOP_TRACKING) {
                    scope.launch {
                        container.stopTracking()
                        refresh++
                    }
                }
            } else if (action == PermissionAction.Ready && configReady) {
                container.startTracking()
                refresh++
            }
        }, enabled = tracking || (action == PermissionAction.Ready && configReady), modifier = Modifier.fillMaxWidth()) {
            Text(if (tracking) "Dừng theo dõi" else "Bắt đầu theo dõi")
        }
        if (!configReady) Text("Cần hoàn tất cấu hình trước khi bắt đầu.", color = MaterialTheme.colorScheme.error)
        Text("Android có thể trì hoãn tác vụ nền; giờ gửi là khoảng dự kiến.")
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
        Text("Cấu hình quản trị", style = MaterialTheme.typography.headlineSmall)
        Text("Device ID: ${container.deviceId.get()}")
        Text("Giám sát GPS: 10 giây")
        Text("Lưu khi đang chạy: 2 phút")
        OutlinedTextField(device, { device = it.filter(Char::isDigit).take(3) }, label = { Text("Số thiết bị 001-100") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(recipient, { recipient = it }, label = { Text("Email nhận") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 12, 24).forEach { hours ->
                FilterChip(interval == hours, { interval = hours }, { Text("${hours}h") })
            }
        }
        OutlinedTextField(sender, { sender = it }, label = { Text("Gmail gửi") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("App Password mới (để trống nếu giữ nguyên)") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button({
            scope.launch {
                val candidate = PilotConfig(
                    device,
                    recipient,
                    interval,
                    sender,
                    password.ifBlank { container.pilotConfig.load().appPassword },
                ).normalized()
                if (!candidate.isValid()) {
                    result = "Cấu hình không hợp lệ"
                } else {
                    val tested = withContext(Dispatchers.IO) { container.gmail.testCredentials(candidate) }
                    result = if (tested == MailResult.Accepted) {
                        container.pilotConfig.save(candidate).getOrThrow()
                        container.reconcileSchedule()
                        password = ""
                        "Đã lưu và đăng nhập Gmail thành công"
                    } else {
                        "Không thể đăng nhập Gmail: ${tested.javaClass.simpleName}"
                    }
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Lưu và kiểm tra") }
        result?.let { Text(it) }
        Text("Đổi PIN", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(currentPin, { currentPin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN hiện tại") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(newPin, { newPin = it.filter(Char::isDigit).take(8) }, label = { Text("PIN mới") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        OutlinedButton({
            result = container.adminPin.change(currentPin, newPin).fold(
                { "Đã đổi PIN" },
                { it.message ?: "Không thể đổi PIN" },
            )
        }, modifier = Modifier.fillMaxWidth()) { Text("Đổi PIN") }
    }
}

@Composable
private fun HistoryScreen(
    container: AppContainer,
    modifier: Modifier,
    requirePin: (ProtectedAction, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val currentYear = remember { Year.now(zone).value }
    val oldestCapturedAt by container.history.observeOldestCapturedAt()
        .collectAsStateWithLifecycle(initialValue = null)
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<Int?>(null) }
    var deleteRequest by remember { mutableStateOf<DeleteRequest?>(null) }
    val filter = remember(selectedYear, selectedMonth) { HistoryFilter(selectedYear, selectedMonth) }
    val range = remember(filter, zone) { filter.range(zone) }
    val recordsFlow = remember(range) { container.history.observeBetween(range.from, range.until) }
    val records by recordsFlow.collectAsStateWithLifecycle(emptyList())
    val oldestYear = oldestCapturedAt?.let {
        Instant.ofEpochMilli(it).atZone(zone).year
    } ?: currentYear
    val yearOptions = remember(oldestYear, currentYear) {
        listOf<Int?>(null) + (oldestYear..currentYear).toList()
    }
    val monthOptions = remember { listOf<Int?>(null) + (1..12).toList() }

    Column(
        modifier.padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Lịch sử dữ liệu", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionDropdown(
                label = "Năm",
                selected = selectedYear,
                options = yearOptions,
                text = { it?.toString() ?: "Tất cả" },
                onSelected = { year ->
                    selectedYear = year
                    if (year == null) selectedMonth = null
                },
                modifier = Modifier.weight(1f),
            )
            SelectionDropdown(
                label = "Tháng",
                selected = selectedMonth,
                options = monthOptions,
                text = { it?.let { month -> "%02d".format(month) } ?: "Tất cả" },
                onSelected = { month ->
                    val normalized = normalizeMonthSelection(month, selectedYear, currentYear)
                    selectedYear = normalized.year
                    selectedMonth = normalized.month
                },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedButton(
            {
                deleteRequest = DeleteRequest(
                    ProtectedAction.DELETE_FILTERED,
                    deleteConfirmationLabel(filter),
                    range,
                )
            },
            enabled = filter.canDeleteFiltered,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Xóa theo bộ lọc") }
        OutlinedButton(
            { deleteRequest = DeleteRequest(ProtectedAction.DELETE_ALL, "Xóa toàn bộ dữ liệu?") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Xóa tất cả") }
        OutlinedButton({
            val file = container.csv.writeCompleteExport(container.pilotConfig.load().deviceNumber, records)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, container.csv.shareUri(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Chia sẻ lịch sử"))
        }, enabled = records.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Xuất CSV theo bộ lọc") }
        records.forEach { record ->
            Column(Modifier.fillMaxWidth()) {
                Text("#${record.id} - ${formatTime(record.capturedAt)} - ${record.recordType.name}")
                Text("Trạng thái gửi: ${record.state.name}")
                Text("Pin ${record.batteryPercent ?: "?"}% | Sai số ${record.accuracy ?: "?"} m")
                Text("Đã theo dõi ${formatDuration(record.trackedDurationMillis)}")
                Text("${record.latitude}, ${record.longitude}")
            }
        }
    }

    deleteRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            title = { Text("Xác nhận xóa") },
            text = { Text("${request.label}\nThao tác này không thể hoàn tác.") },
            confirmButton = {
                Button({
                    deleteRequest = null
                    requirePin(request.action) {
                        scope.launch {
                            request.range?.let { container.history.deleteBetween(it.from, it.until) }
                                ?: container.history.deleteAll()
                        }
                    }
                }) { Text("Tiếp tục") }
            },
            dismissButton = {
                OutlinedButton({ deleteRequest = null }) { Text("Hủy") }
            },
        )
    }
}

@Composable
private fun SelectionDropdown(
    label: String,
    selected: Int?,
    options: List<Int?>,
    text: (Int?) -> String,
    onSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton({ expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${text(selected)}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) { Text(label); Text(value) }

private fun permissionSnapshot(activity: Activity, fineRequested: Boolean, refresh: Int): PermissionSnapshot {
    refresh.hashCode()
    val fine = ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
    return PermissionSnapshot(
        activity.getSystemService(LocationManager::class.java).isLocationEnabled,
        fine,
        ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED,
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        fineRequested && !fine && !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
    )
}

private fun pinDialogTitle(action: ProtectedAction): String = when (action) {
    ProtectedAction.OPEN_SETTINGS -> "Mở khóa Cấu hình"
    ProtectedAction.STOP_TRACKING -> "Xác nhận dừng theo dõi"
    ProtectedAction.DELETE_FILTERED, ProtectedAction.DELETE_ALL -> "Xác nhận xóa dữ liệu"
}

private fun openAppSettings(activity: Activity) = activity.startActivity(
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
)

private fun formatTime(value: Long) = if (value == 0L) {
    "Chưa có"
} else {
    DateFormat.getDateTimeInstance().format(Date(value))
}

private fun formatDuration(value: Long): String {
    val minutes = value.coerceAtLeast(0) / 60_000
    return "${minutes / 1_440}d ${minutes / 60 % 24}h ${minutes % 60}m"
}
