/*
 * Copyright 2018-2022 SIP3.IO, Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sip3.captain.ce.util

import io.netty.buffer.ByteBuf

object SmppUtil {

    val COMMANDS = setOf(
        0x80000000,             // CMD_ID_GENERIC_NACK
        0x00000001, 0x80000001, // CMD_ID_BIND_RECEIVER
        0x00000002, 0x80000002, // CMD_ID_BIND_TRANSMITTER
        0x00000003, 0x80000003, // CMD_ID_QUERY_SM
        0x00000004, 0x80000004, // CMD_ID_SUBMIT_SM
        0x00000005, 0x80000005, // CMD_ID_DELIVER_SM
        0x00000006, 0x80000006, // CMD_ID_UNBIND
        0x00000007, 0x80000007, // CMD_ID_REPLACE_SM
        0x00000008, 0x80000008, // CMD_ID_CANCEL_SM
        0x00000009, 0x80000009, // CMD_ID_BIND_TRANSCEIVER
        0x0000000b, 0x8000000b, // CMD_ID_OUTBIND
        0x00000015, 0x80000015, // CMD_ID_ENQUIRE_LINK
        0x00000021, 0x80000021, // CMD_ID_SUBMIT_MULTI
        0x00000102, 0x80000102, // CMD_ID_ALERT_NOTIFICATION
        0x00000103, 0x80000103, // CMD_ID_DATA_SM
        0x00000111, 0x80000111, // CMD_ID_BROADCAST_SM
        0x00000112, 0x80000112, // CMD_ID_QUERY_BROADCAST_SM
        0x00000113, 0x80000113  // CMD_ID_CANCEL_BROADCAST_SM
    )

    fun isPdu(buffer: ByteBuf): Boolean {
        if (!checkMinPduLength(buffer)) {
            return false
        }

        val offset = buffer.readerIndex()

        val length = buffer.getUnsignedInt(offset).toInt()
        val command = buffer.getUnsignedInt(offset + 4)

        return buffer.readableBytes() == length && isPduCommand(command)
    }

    fun checkMinPduLength(buffer: ByteBuf): Boolean {
        return buffer.readableBytes() >= 16
    }

    fun isPduCommand(commandId: Long): Boolean {
        return COMMANDS.contains(commandId)
    }
}