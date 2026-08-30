package dev.shizzi

enum class AutomationCommand { START, STOP, TOGGLE, QUERY_STATUS }

sealed interface AutomationRefusal {
    data object Disabled : AutomationRefusal
    data object BadToken : AutomationRefusal
    data object UnknownAction : AutomationRefusal
}

object Automation {

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

    fun commandFor(action: String?): AutomationCommand? = when (action) {
        ACTION_START -> AutomationCommand.START
        ACTION_STOP -> AutomationCommand.STOP
        ACTION_TOGGLE -> AutomationCommand.TOGGLE
        ACTION_QUERY_STATUS -> AutomationCommand.QUERY_STATUS
        else -> null
    }

    fun refuse(settings: Settings, presented: String?): AutomationRefusal? = when {
        !settings.isAutomationEnabled -> AutomationRefusal.Disabled
        !isTokenAccepted(settings.automationToken, presented) -> AutomationRefusal.BadToken
        else -> null
    }

    private fun isTokenAccepted(expected: String, presented: String?): Boolean {
        if (expected.isEmpty()) return false
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

    fun describe(refusal: AutomationRefusal): String = when (refusal) {
        AutomationRefusal.Disabled ->
            "automation is off; enable it in Settings › Automation"
        AutomationRefusal.BadToken -> "token does not match the one set in Settings"
        AutomationRefusal.UnknownAction -> "unrecognised action"
    }
}
