package dev.shizzi

// Check and install details travel in English from the privileged process; describe() translates
// the ones the app writes itself and leaves system output, such as pm's, as reported.
object CompatibilityDetail {

    const val NOT_REPORTED = "not reported by the privileged process"

    const val TEST_NETWORK_UNAVAILABLE = "test_network service or TestNetworkManager class unavailable"

    const val INSTALL_TIMED_OUT = "pm install did not exit within"

    private val known = mapOf(
        NOT_REPORTED to R.string.compatibility_not_reported,
        TEST_NETWORK_UNAVAILABLE to R.string.compatibility_test_network_unavailable,
    )

    fun describe(detail: String): UiText {
        known[detail]?.let { return UiText.Resource(it) }
        if (!detail.startsWith(INSTALL_TIMED_OUT)) return UiText.Verbatim(detail)

        val timedOut = UiText.Resource(R.string.module_install_timed_out)
        val output = detail.substringAfter('\n', missingDelimiterValue = "").trim()

        return when {
            output.isEmpty() -> timedOut
            else -> UiText.Resource(R.string.detail_with_output, listOf(timedOut, output))
        }
    }
}
