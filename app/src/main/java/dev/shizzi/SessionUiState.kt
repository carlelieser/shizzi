package dev.shizzi

import org.json.JSONObject

enum class UiStatus { READY, LOADING, CONNECTED, ERROR }

data class SessionUiState(
    val shizukuState: ShizukuState = ShizukuState.NotRunning,
    val status: UiStatus = UiStatus.READY,
    val isBusy: Boolean = false,
    val detail: String = "",
    val interfaceName: String = "",
    val lastError: String = "",
    val isVpnBound: Boolean = false,
    val isVpnBypassed: Boolean = false,
    val isWaitingForUsb: Boolean = false,

    val clientCount: Int = 0,

    val traffic: Traffic = Traffic(),
) {

    val canStart: Boolean get() = shizukuState is ShizukuState.Ready && !isBusy
}

fun SessionUiState.asStopped(): SessionUiState = copy(
    isBusy = false,
    status = UiStatus.READY,
    lastError = "",
    detail = "Stopped",
    interfaceName = "",
    isVpnBound = false,
    isVpnBypassed = false,
    isWaitingForUsb = false,
    clientCount = 0,
    traffic = Traffic(),
)

fun SessionUiState.applyOutcome(outcome: Result<String>): SessionUiState {
    val failure = outcome.exceptionOrNull()
    if (failure != null) {
        return copy(
            isBusy = false,
            status = UiStatus.ERROR,
            lastError = "${failure.javaClass.simpleName}: ${failure.message}",

            interfaceName = "",
            isVpnBound = false,
            isVpnBypassed = false,
            isWaitingForUsb = false,
            clientCount = 0,
            traffic = Traffic(),
        )
    }

    val parsed = runCatching { JSONObject(outcome.getOrDefault("{}")) }.getOrNull()
    val sessionState = parsed?.optString("state").orEmpty()
    val sessionDetail = parsed?.optString("detail").orEmpty()

    return copy(
        isBusy = false,
        status = statusFor(sessionState),
        detail = sessionDetail,
        interfaceName = parsed?.optString("interface").orEmpty().takeIf { it != "null" }.orEmpty(),
        lastError = if (sessionState == "ERROR") sessionDetail else "",

        isVpnBound = parsed?.optBoolean("isVpnBound") == true,
        isVpnBypassed = parsed?.optBoolean("isVpnBypassed") == true,
        isWaitingForUsb = parsed?.optBoolean("isWaitingForUsb") == true,
        clientCount = parsed?.optInt("clientCount") ?: 0,
        traffic = Traffic(
            up = parsed?.optLong("bytesUp") ?: 0,
            down = parsed?.optLong("bytesDown") ?: 0,
        ),
    )
}

private fun statusFor(sessionState: String): UiStatus = when (sessionState) {
    "ACTIVE" -> UiStatus.CONNECTED
    "ERROR" -> UiStatus.ERROR
    "STARTING" -> UiStatus.LOADING
    else -> UiStatus.READY
}
