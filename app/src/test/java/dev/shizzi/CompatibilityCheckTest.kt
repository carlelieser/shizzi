package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CompatibilityCheckTest {

    @Test
    fun `per capability entries are parsed into results`() {
        val report = """
            {
              "TEST_NETWORK": {"present": true, "detail": "test_network service found"},
              "PREFER_TEST_NETWORKS": {"present": false, "detail": "NoSuchMethodError: setPreferTestNetworks"}
            }
        """.trimIndent()

        val results = parseCapabilities(report)

        assertEquals(
            CapabilityResult(Capability.TEST_NETWORK, true, "test_network service found"),
            results.first { it.capability == Capability.TEST_NETWORK },
        )
        assertEquals(
            CapabilityResult(Capability.PREFER_TEST_NETWORKS, false, "NoSuchMethodError: setPreferTestNetworks"),
            results.first { it.capability == Capability.PREFER_TEST_NETWORKS },
        )
    }

    @Test
    fun `an error report from the privileged process surfaces the real failure`() {
        val report = """
            {
              "verdict": "ERROR",
              "operation": "checkCompatibility",
              "error": "java.lang.IllegalStateException: forceOpPackageName: could not attribute context"
            }
        """.trimIndent()

        val results = parseCapabilities(report)

        results.forEach { result ->
            assertFalse(result.isPresent)
            assertEquals(
                "the privileged process failed: java.lang.IllegalStateException: " +
                    "forceOpPackageName: could not attribute context",
                result.detail,
            )
        }
    }

    @Test
    fun `a report without capability entries falls back to the generic detail`() {
        val results = parseCapabilities("{}")

        results.forEach { result ->
            assertFalse(result.isPresent)
            assertEquals("not reported by the privileged process", result.detail)
        }
    }
}
