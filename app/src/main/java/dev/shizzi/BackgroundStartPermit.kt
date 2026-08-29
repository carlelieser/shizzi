package dev.shizzi

import android.content.Context
import android.os.Build
import android.os.PowerManager

object BackgroundStartPermit {

    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isHeld(context: Context): Boolean {
        if (!isRequired) return true

        val power = context.getSystemService(PowerManager::class.java)
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun grant(packageName: String): String? {
        if (!isRequired) return null

        return runCatching { allowlist(packageName) }
            .fold(
                onSuccess = { null },
                onFailure = { failure -> "${failure.javaClass.simpleName}: ${failure.message}" },
            )
    }

    private fun allowlist(packageName: String) {
        val command = arrayOf("dumpsys", "deviceidle", "whitelist", "+$packageName")

        val process = Runtime.getRuntime().exec(command)
        val error = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()

        check(code == 0) { "deviceidle whitelist failed ($code): ${error.trim()}" }
    }
}
