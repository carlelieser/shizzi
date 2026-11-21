package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Test

class HelperSuccessionTest {

    private val helper = "dev.shizzi:probe"

    @Test
    fun `finds other helpers but not itself`() {
        val commandLines = mapOf(
            100 to "$helper\u0000",
            200 to "$helper\u0000",
            300 to "$helper${" ".repeat(40)}",
        )

        assertEquals(listOf(100, 300), otherHelperPids(commandLines, ownPid = 200, processName = helper))
    }

    @Test
    fun `ignores the app and unrelated processes`() {
        val commandLines = mapOf(
            100 to "dev.shizzi\u0000",
            101 to "dev.shizzi:probe2\u0000",
            102 to "/system/bin/sh\u0000-c\u0000dev.shizzi:probe\u0000",
            103 to "",
        )

        assertEquals(emptyList<Int>(), otherHelperPids(commandLines, ownPid = 1, processName = helper))
    }
}
