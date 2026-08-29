package dev.shizzi

import android.os.Build

object BackgroundStartPermit {

    private const val APP_OP = "START_FOREGROUND_SERVICES_FROM_BACKGROUND"

    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun grant(packageName: String): String? {
        if (!isRequired) return null

        return runCatching { appops("set", packageName, "allow") }
            .fold(
                onSuccess = { null },
                onFailure = { failure -> "${failure.javaClass.simpleName}: ${failure.message}" },
            )
    }

    fun isHeld(packageName: String): Boolean {
        if (!isRequired) return true

        return runCatching { appops("get", packageName) }
            .getOrDefault("")
            .lowercase()
            .contains("allow")
    }

    private fun appops(operation: String, packageName: String, mode: String? = null): String {
        val command = listOfNotNull("appops", operation, packageName, APP_OP, mode)

        val process = Runtime.getRuntime().exec(command.toTypedArray())
        val output = process.inputStream.bufferedReader().readText()
        val error = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()

        check(code == 0) { "appops $operation failed ($code): ${error.trim()}" }
        return output
    }
}
