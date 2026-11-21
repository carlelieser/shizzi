package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class CompatibilityDetailTest {

    @Test
    fun `translates a check the privileged process did not report`() {
        val unreported = parseCapabilities("{}").first().detail

        assertEquals(UiText.Resource(R.string.compatibility_not_reported), CompatibilityDetail.describe(unreported))
    }

    @Test
    fun `translates a missing test network service`() {
        assertEquals(
            UiText.Resource(R.string.compatibility_test_network_unavailable),
            CompatibilityDetail.describe(CompatibilityDetail.TEST_NETWORK_UNAVAILABLE),
        )
    }

    @Test
    fun `keeps installer output under a translated timeout`() {
        val detail = "${CompatibilityDetail.INSTALL_TIMED_OUT} 120000ms\nWaiting for session"
        val timedOut = UiText.Resource(R.string.module_install_timed_out)

        assertEquals(
            UiText.Resource(R.string.detail_with_output, listOf(timedOut, "Waiting for session")),
            CompatibilityDetail.describe(detail),
        )
        assertEquals(timedOut, CompatibilityDetail.describe("${CompatibilityDetail.INSTALL_TIMED_OUT} 120000ms\n"))
    }

    @Test
    fun `shows system output as reported`() {
        val failure = "Failure [INSTALL_FAILED_VERIFICATION_FAILURE]"

        assertEquals(UiText.Verbatim(failure), CompatibilityDetail.describe(failure))
    }
}
