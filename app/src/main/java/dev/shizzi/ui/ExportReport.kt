package dev.shizzi.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.shizzi.BuildConfig
import dev.shizzi.SessionLog
import java.io.File

/** Subdirectory of files/, matching the authority's declared path. */
private const val EXPORT_DIR = "exports"

/** One name, overwritten per export: the last report is the only one worth keeping. */
private const val EXPORT_NAME = "shizzi-probe-report.json"

/**
 * Writes its own copy from the [report] string rather than sharing the shell's
 * file: that lives in /data/local/tmp, which this process cannot read, and a
 * FileProvider can only serve paths inside app storage anyway.
 *
 * Failures are logged, not thrown — the user can already see where the report
 * landed, so crashing because no app accepts JSON costs more than it saves.
 */
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
        // The receiving process has no claim on this file; the grant is what
        // lets it read the uri, and it lasts only as long as the intent.
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
