package dev.shizzi

fun describeSession(elapsedMs: Long, traffic: Traffic): String =
    "session summary: active ${formatDuration(elapsedMs)}, " +
        "${Traffic.format(traffic.up)} up, ${Traffic.format(traffic.down)} down"

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
