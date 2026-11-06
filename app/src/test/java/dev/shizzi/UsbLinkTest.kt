package dev.shizzi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbLinkTest {

    @Test
    fun `reads the device connection, not the host or port one`() {
        val dump = """
            USB MANAGER STATE (dumpsys usb):
              device_manager={
                handler={
                  current_functions=ncm
                  connected=false
                  configured=false
                  host_connected=true
                }
              }
              host_manager={
              }
              port_manager={
                connected=true
              }
        """.trimIndent()

        assertEquals(false, parseUsbHostConnected(dump))
    }

    @Test
    fun `reports a connected host`() {
        val dump = "  device_manager={\n    connected=true\n    configured=true\n  }\n  host_manager={\n  }"

        assertEquals(true, parseUsbHostConnected(dump))
    }

    @Test
    fun `unknown when the device block is missing`() {
        assertNull(parseUsbHostConnected("Can't find service: usb"))
    }
}
