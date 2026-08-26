package com.internal.tracker.export

import android.content.Context
import androidx.core.content.FileProvider
import com.internal.tracker.history.LocationRecord
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

class DailyCsvStore(private val context: Context) {
    private val directory: File
        get() = requireNotNull(context.getExternalFilesDir("reports")) { "Khong the mo thu muc bao cao" }

    fun writeDay(deviceNumber: String, date: LocalDate, records: List<LocationRecord>): File {
        val target = File(directory, "GPS-$deviceNumber-$date.csv")
        val temporary = File(directory, ".${target.name}.tmp")
        directory.mkdirs()
        temporary.writeText(LocationCsv.encode(records), Charsets.UTF_8)
        try {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        return target
    }

    fun shareUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun writeCompleteExport(deviceNumber: String, records: List<LocationRecord>): File {
        directory.mkdirs()
        return File(directory, "GPS-$deviceNumber-all.csv").apply {
            writeText(LocationCsv.encode(records), Charsets.UTF_8)
        }
    }
}
