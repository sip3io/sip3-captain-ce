package io.sip3.captain.ce.util

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.net.InetAddress

class IpUtilTest {

    @Test
    fun `convert valid IPv4 address to Int`() {
        val addr = InetAddress.getByName("23.08.20.15")
        assertEquals(386405391, IpUtil.convertToInt(addr.address))
    }

    @Test
    fun `convert invalid address to Int`() {
        Assertions.assertThrows(UnsupportedOperationException::class.java) {
            val addr = byteArrayOf(0x01, 0x02, 0x03)
            IpUtil.convertToInt(addr)
        }
    }
}