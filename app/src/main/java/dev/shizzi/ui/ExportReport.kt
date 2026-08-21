package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.shizzi.BuildConfig
import dev.shizzi.SessionLog
import java.io.File

private const val EXPORT_DIR = "exports"

private const val EXPORT_NAME = "shizzi-probe-report.json"

fun Context.exportReport(report: String) {
    val uri = runCatching { writeExport(report) }
        .getOrElse { failure ->
            SessionLog.warn("could not stage the report for export: ${failure.message}")
            return
        }

    val intent = Intent(Intent.ACTION_SEND)
        .setType("application/json")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, EXPORT_NAME)

        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    try {
        startActivity(Intent.createChooser(intent, "Export report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (absent: ActivityNotFoundException) {
        SessionLog.warn("no app to receive the report: ${absent.message}")
    }
}

private fun Context.writeExport(report: String): android.net.Uri {
    val directory = File(filesDir, EXPORT_DIR).apply { mkdirs() }
    val file = File(directory, EXPORT_NAME)
    file.writeText(report)

    return FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.exports", file)
}
