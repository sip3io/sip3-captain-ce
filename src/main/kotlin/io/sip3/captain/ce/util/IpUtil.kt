package io.sip3.captain.ce.util

object IpUtil {

    fun convertToInt(addr: ByteArray): Int {
        if (addr.size != 4) {
            throw UnsupportedOperationException("Can't convert ${addr.size} bytes address to Int")
        }

        var number = 0
        repeat(4) { i ->
            number = (number shl 8) + addr[i]
        }

        return number
    }
}
