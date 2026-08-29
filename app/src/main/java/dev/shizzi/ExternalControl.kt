package dev.shizzi

enum class ExternalCommand { START, STOP, TOGGLE, QUERY_STATUS }

sealed interface ExternalRefusal {
    data object Disabled : ExternalRefusal
    data object BadToken : ExternalRefusal
    data object UnknownAction : ExternalRefusal
}

object ExternalControl {

    const val ACTION_START = "dev.shizzi.action.START"
    const val ACTION_STOP = "dev.shizzi.action.STOP"
    const val ACTION_TOGGLE = "dev.shizzi.action.TOGGLE"
    const val ACTION_QUERY_STATUS = "dev.shizzi.action.QUERY_STATUS"

    const val ACTION_RESULT = "dev.shizzi.action.SESSION_RESULT"

    const val EXTRA_TOKEN = "token"

    const val EXTRA_COMMAND = "command"
    const val EXTRA_ACCEPTED = "accepted"
    const val EXTRA_REFUSAL = "refusal"
    const val EXTRA_STATUS = "status"
    const val EXTRA_IS_ACTIVE = "isActive"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_INTERFACE = "interface"
    const val EXTRA_ERROR = "error"
    const val EXTRA_CLIENT_COUNT = "clientCount"
    const val EXTRA_BYTES_UP = "bytesUp"
    const val EXTRA_BYTES_DOWN = "bytesDown"

    fun commandFor(action: String?): ExternalCommand? = when (action) {
        ACTION_START -> ExternalCommand.START
        ACTION_STOP -> ExternalCommand.STOP
        ACTION_TOGGLE -> ExternalCommand.TOGGLE
        ACTION_QUERY_STATUS -> ExternalCommand.QUERY_STATUS
        else -> null
    }

    fun refuse(settings: Settings, presented: String?): ExternalRefusal? = when {
        !settings.isExternalControlEnabled -> ExternalRefusal.Disabled
        !isTokenAccepted(settings.externalControlToken, presented) -> ExternalRefusal.BadToken
        else -> null
    }

    private fun isTokenAccepted(expected: String, presented: String?): Boolean {
        if (expected.isEmpty()) return true
        return presented != null && isEqualInConstantTime(expected, presented)
    }

    private fun isEqualInConstantTime(expected: String, presented: String): Boolean {
        val expectedBytes = expected.toByteArray()
        val presentedBytes = presented.toByteArray()
        if (expectedBytes.size != presentedBytes.size) return false

        var difference = 0
        expectedBytes.indices.forEach { index ->
            difference = difference or (expectedBytes[index].toInt() xor presentedBytes[index].toInt())
        }
        return difference == 0
    }

    fun describe(refusal: ExternalRefusal): String = when (refusal) {
        ExternalRefusal.Disabled ->
            "external control is off; enable it in Settings › External control"
        ExternalRefusal.BadToken -> "token does not match the one set in Settings"
        ExternalRefusal.UnknownAction -> "unrecognised action"
    }
}
