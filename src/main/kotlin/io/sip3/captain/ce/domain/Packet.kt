package io.sip3.captain.ce.domain

import java.sql.Timestamp

class Packet {

    companion object {

        const val TYPE_RTCP: Byte = 1
        const val TYPE_RTP: Byte = 2
        const val TYPE_SIP: Byte = 3
        const val TYPE_ICMP: Byte = 4
    }

    lateinit var timestamp: Timestamp
    lateinit var srcAddr: ByteArray
    lateinit var dstAddr: ByteArray
    var srcPort: Int = 0
    var dstPort: Int = 0
    var protocolCode: Byte = 0
    lateinit var payload: Payload
}