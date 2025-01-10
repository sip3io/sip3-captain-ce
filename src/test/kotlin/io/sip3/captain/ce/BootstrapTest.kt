/*
 * Copyright 2018-2025 SIP3.IO, Corp.
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

package io.sip3.captain.ce

import io.sip3.captain.ce.encoder.Encoder
import io.sip3.commons.ProtocolCodes
import io.sip3.commons.vertx.test.VertxTest
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.datagram.datagramSocketOptionsOf
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pcap4j.core.Pcaps
import java.net.InetAddress

class BootstrapTest : VertxTest() {

    companion object {


        val PACKET = byteArrayOf(
            0x49.toByte(), 0x4e.toByte(), 0x56.toByte(), 0x49.toByte(), 0x54.toByte(), 0x45.toByte(), 0x20.toByte(),
            0x73.toByte(), 0x69.toByte(), 0x70.toByte(), 0x3a.toByte(), 0x37.toByte(), 0x32.toByte(), 0x34.toByte(),
            0x30.toByte(), 0x32.toByte(), 0x32.toByte(), 0x33.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(),
            0x30.toByte(), 0x31.toByte(), 0x34.toByte(), 0x31.toByte(), 0x30.toByte(), 0x34.toByte(), 0x35.toByte(),
            0x33.toByte(), 0x36.toByte(), 0x40.toByte(), 0x64.toByte(), 0x65.toByte(), 0x6d.toByte(), 0x6f.toByte(),
            0x2e.toByte(), 0x73.toByte(), 0x69.toByte(), 0x70.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x69.toByte(),
            0x6f.toByte(), 0x20.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(),
            0x2e.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x56.toByte(), 0x69.toByte(), 0x61.toByte(),
            0x3a.toByte(), 0x20.toByte(), 0x53.toByte(), 0x49.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x32.toByte(),
            0x2e.toByte(), 0x30.toByte(), 0x2f.toByte(), 0x55.toByte(), 0x44.toByte(), 0x50.toByte(), 0x20.toByte(),
            0x64.toByte(), 0x65.toByte(), 0x6d.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x73.toByte(), 0x69.toByte(),
            0x70.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x3a.toByte(), 0x34.toByte(),
            0x30.toByte(), 0x35.toByte(), 0x37.toByte(), 0x36.toByte(), 0x3b.toByte(), 0x62.toByte(), 0x72.toByte(),
            0x61.toByte(), 0x6e.toByte(), 0x63.toByte(), 0x68.toByte(), 0x3d.toByte(), 0x7a.toByte(), 0x39.toByte(),
            0x68.toByte(), 0x47.toByte(), 0x34.toByte(), 0x62.toByte(), 0x4b.toByte(), 0x2e.toByte(), 0x41.toByte(),
            0x77.toByte(), 0x38.toByte(), 0x46.toByte(), 0x6c.toByte(), 0x43.toByte(), 0x57.toByte(), 0x65.toByte(),
            0x4b.toByte(), 0x3b.toByte(), 0x72.toByte(), 0x70.toByte(), 0x6f.toByte(), 0x72.toByte(), 0x74.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x43.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x2d.toByte(),
            0x49.toByte(), 0x44.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x62.toByte(), 0x32.toByte(), 0x63.toByte(),
            0x36.toByte(), 0x39.toByte(), 0x32.toByte(), 0x33.toByte(), 0x31.toByte(), 0x63.toByte(), 0x32.toByte(),
            0x39.toByte(), 0x30.toByte(), 0x61.toByte(), 0x33.toByte(), 0x39.toByte(), 0x30.toByte(), 0x31.toByte(),
            0x37.toByte(), 0x33.toByte(), 0x31.toByte(), 0x34.toByte(), 0x34.toByte(), 0x36.toByte(), 0x35.toByte(),
            0x37.toByte(), 0x66.toByte(), 0x31.toByte(), 0x65.toByte(), 0x66.toByte(), 0x38.toByte(), 0x39.toByte(),
            0x64.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x46.toByte(), 0x72.toByte(), 0x6f.toByte(), 0x6d.toByte(),
            0x3a.toByte(), 0x20.toByte(), 0x3c.toByte(), 0x73.toByte(), 0x69.toByte(), 0x70.toByte(), 0x3a.toByte(),
            0x36.toByte(), 0x37.toByte(), 0x32.toByte(), 0x39.toByte(), 0x32.toByte(), 0x33.toByte(), 0x34.toByte(),
            0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x38.toByte(), 0x37.toByte(), 0x34.toByte(), 0x37.toByte(),
            0x34.toByte(), 0x39.toByte(), 0x33.toByte(), 0x36.toByte(), 0x30.toByte(), 0x40.toByte(), 0x64.toByte(),
            0x65.toByte(), 0x6d.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x73.toByte(), 0x69.toByte(), 0x70.toByte(),
            0x33.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x3e.toByte(), 0x3b.toByte(), 0x74.toByte(),
            0x61.toByte(), 0x67.toByte(), 0x3d.toByte(), 0x55.toByte(), 0x78.toByte(), 0x4b.toByte(), 0x4a.toByte(),
            0x51.toByte(), 0x46.toByte(), 0x37.toByte(), 0x7e.toByte(), 0x45.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x54.toByte(), 0x6f.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x3c.toByte(), 0x73.toByte(), 0x69.toByte(),
            0x70.toByte(), 0x3a.toByte(), 0x37.toByte(), 0x32.toByte(), 0x34.toByte(), 0x30.toByte(), 0x32.toByte(),
            0x32.toByte(), 0x33.toByte(), 0x38.toByte(), 0x30.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(),
            0x34.toByte(), 0x31.toByte(), 0x30.toByte(), 0x34.toByte(), 0x35.toByte(), 0x33.toByte(), 0x36.toByte(),
            0x40.toByte(), 0x64.toByte(), 0x65.toByte(), 0x6d.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x73.toByte(),
            0x69.toByte(), 0x70.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x3e.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x43.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x61.toByte(),
            0x63.toByte(), 0x74.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x3c.toByte(), 0x73.toByte(), 0x69.toByte(),
            0x70.toByte(), 0x3a.toByte(), 0x36.toByte(), 0x37.toByte(), 0x32.toByte(), 0x39.toByte(), 0x32.toByte(),
            0x33.toByte(), 0x34.toByte(), 0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x38.toByte(), 0x37.toByte(),
            0x34.toByte(), 0x37.toByte(), 0x34.toByte(), 0x39.toByte(), 0x33.toByte(), 0x36.toByte(), 0x30.toByte(),
            0x40.toByte(), 0x64.toByte(), 0x65.toByte(), 0x6d.toByte(), 0x6f.toByte(), 0x2e.toByte(), 0x73.toByte(),
            0x69.toByte(), 0x70.toByte(), 0x33.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x3a.toByte(),
            0x31.toByte(), 0x31.toByte(), 0x32.toByte(), 0x33.toByte(), 0x36.toByte(), 0x3b.toByte(), 0x74.toByte(),
            0x72.toByte(), 0x61.toByte(), 0x6e.toByte(), 0x73.toByte(), 0x70.toByte(), 0x6f.toByte(), 0x72.toByte(),
            0x74.toByte(), 0x3d.toByte(), 0x75.toByte(), 0x64.toByte(), 0x70.toByte(), 0x3e.toByte(), 0x3b.toByte(),
            0x74.toByte(), 0x72.toByte(), 0x61.toByte(), 0x6e.toByte(), 0x73.toByte(), 0x70.toByte(), 0x6f.toByte(),
            0x72.toByte(), 0x74.toByte(), 0x3d.toByte(), 0x75.toByte(), 0x64.toByte(), 0x70.toByte(), 0x3b.toByte(),
            0x65.toByte(), 0x78.toByte(), 0x70.toByte(), 0x69.toByte(), 0x72.toByte(), 0x65.toByte(), 0x73.toByte(),
            0x3d.toByte(), 0x35.toByte(), 0x30.toByte(), 0x3b.toByte(), 0x2b.toByte(), 0x73.toByte(), 0x69.toByte(),
            0x70.toByte(), 0x2e.toByte(), 0x69.toByte(), 0x6e.toByte(), 0x73.toByte(), 0x74.toByte(), 0x61.toByte(),
            0x6e.toByte(), 0x63.toByte(), 0x65.toByte(), 0x3d.toByte(), 0x22.toByte(), 0x3c.toByte(), 0x75.toByte(),
            0x72.toByte(), 0x6e.toByte(), 0x3a.toByte(), 0x75.toByte(), 0x75.toByte(), 0x69.toByte(), 0x64.toByte(),
            0x3a.toByte(), 0x36.toByte(), 0x33.toByte(), 0x36.toByte(), 0x31.toByte(), 0x32.toByte(), 0x38.toByte(),
            0x65.toByte(), 0x64.toByte(), 0x2d.toByte(), 0x36.toByte(), 0x64.toByte(), 0x30.toByte(), 0x36.toByte(),
            0x2d.toByte(), 0x34.toByte(), 0x30.toByte(), 0x34.toByte(), 0x37.toByte(), 0x2d.toByte(), 0x61.toByte(),
            0x65.toByte(), 0x38.toByte(), 0x32.toByte(), 0x2d.toByte(), 0x39.toByte(), 0x39.toByte(), 0x37.toByte(),
            0x62.toByte(), 0x63.toByte(), 0x64.toByte(), 0x33.toByte(), 0x34.toByte(), 0x38.toByte(), 0x34.toByte(),
            0x33.toByte(), 0x36.toByte(), 0x3e.toByte(), 0x22.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x55.toByte(),
            0x73.toByte(), 0x65.toByte(), 0x72.toByte(), 0x2d.toByte(), 0x41.toByte(), 0x67.toByte(), 0x65.toByte(),
            0x6e.toByte(), 0x74.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x44.toByte(), 0x65.toByte(), 0x6d.toByte(),
            0x6f.toByte(), 0x41.toByte(), 0x70.toByte(), 0x70.toByte(), 0x6c.toByte(), 0x69.toByte(), 0x63.toByte(),
            0x61.toByte(), 0x74.toByte(), 0x69.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x5f.toByte(), 0x41.toByte(),
            0x6e.toByte(), 0x64.toByte(), 0x72.toByte(), 0x6f.toByte(), 0x69.toByte(), 0x64.toByte(), 0x2f.toByte(),
            0x37.toByte(), 0x2e.toByte(), 0x30.toByte(), 0x5f.toByte(), 0x4f.toByte(), 0x53.toByte(), 0x5f.toByte(),
            0x33.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x38.toByte(), 0x20.toByte(), 0x28.toByte(),
            0x62.toByte(), 0x65.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x65.toByte(), 0x2d.toByte(), 0x73.toByte(),
            0x69.toByte(), 0x70.toByte(), 0x2f.toByte(), 0x31.toByte(), 0x2e.toByte(), 0x36.toByte(), 0x2e.toByte(),
            0x33.toByte(), 0x29.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x43.toByte(), 0x53.toByte(), 0x65.toByte(),
            0x71.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x32.toByte(), 0x30.toByte(), 0x20.toByte(), 0x49.toByte(),
            0x4e.toByte(), 0x56.toByte(), 0x49.toByte(), 0x54.toByte(), 0x45.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x4d.toByte(), 0x61.toByte(), 0x78.toByte(), 0x2d.toByte(), 0x46.toByte(), 0x6f.toByte(), 0x72.toByte(),
            0x77.toByte(), 0x61.toByte(), 0x72.toByte(), 0x64.toByte(), 0x73.toByte(), 0x3a.toByte(), 0x20.toByte(),
            0x37.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x43.toByte(), 0x6f.toByte(), 0x6e.toByte(),
            0x74.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x2d.toByte(), 0x54.toByte(), 0x79.toByte(),
            0x70.toByte(), 0x65.toByte(), 0x3a.toByte(), 0x20.toByte(), 0x61.toByte(), 0x70.toByte(), 0x70.toByte(),
            0x6c.toByte(), 0x69.toByte(), 0x63.toByte(), 0x61.toByte(), 0x74.toByte(), 0x69.toByte(), 0x6f.toByte(),
            0x6e.toByte(), 0x2f.toByte(), 0x73.toByte(), 0x64.toByte(), 0x70.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x43.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x74.toByte(),
            0x2d.toByte(), 0x4c.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x67.toByte(), 0x74.toByte(), 0x68.toByte(),
            0x3a.toByte(), 0x20.toByte(), 0x33.toByte(), 0x34.toByte(), 0x38.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x76.toByte(), 0x3d.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x6f.toByte(), 0x3d.toByte(), 0x2d.toByte(), 0x20.toByte(), 0x32.toByte(), 0x33.toByte(), 0x30.toByte(),
            0x38.toByte(), 0x32.toByte(), 0x30.toByte(), 0x31.toByte(), 0x35.toByte(), 0x20.toByte(), 0x33.toByte(),
            0x31.toByte(), 0x34.toByte(), 0x30.toByte(), 0x36.toByte(), 0x37.toByte(), 0x34.toByte(), 0x33.toByte(),
            0x32.toByte(), 0x39.toByte(), 0x20.toByte(), 0x49.toByte(), 0x4e.toByte(), 0x20.toByte(), 0x49.toByte(),
            0x50.toByte(), 0x34.toByte(), 0x20.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x31.toByte(),
            0x30.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x30.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x73.toByte(), 0x3d.toByte(), 0x6d.toByte(), 0x65.toByte(), 0x64.toByte(),
            0x69.toByte(), 0x61.toByte(), 0x2d.toByte(), 0x73.toByte(), 0x65.toByte(), 0x72.toByte(), 0x76.toByte(),
            0x65.toByte(), 0x72.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x63.toByte(), 0x3d.toByte(), 0x49.toByte(),
            0x4e.toByte(), 0x20.toByte(), 0x49.toByte(), 0x50.toByte(), 0x34.toByte(), 0x20.toByte(), 0x31.toByte(),
            0x30.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x30.toByte(), 0x2e.toByte(), 0x31.toByte(), 0x30.toByte(),
            0x2e.toByte(), 0x31.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x74.toByte(), 0x3d.toByte(),
            0x30.toByte(), 0x20.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x61.toByte(), 0x3d.toByte(),
            0x72.toByte(), 0x74.toByte(), 0x63.toByte(), 0x70.toByte(), 0x2d.toByte(), 0x78.toByte(), 0x72.toByte(),
            0x3a.toByte(), 0x72.toByte(), 0x63.toByte(), 0x76.toByte(), 0x72.toByte(), 0x2d.toByte(), 0x72.toByte(),
            0x74.toByte(), 0x74.toByte(), 0x3d.toByte(), 0x61.toByte(), 0x6c.toByte(), 0x6c.toByte(), 0x3a.toByte(),
            0x31.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x20.toByte(), 0x73.toByte(),
            0x74.toByte(), 0x61.toByte(), 0x74.toByte(), 0x2d.toByte(), 0x73.toByte(), 0x75.toByte(), 0x6d.toByte(),
            0x6d.toByte(), 0x61.toByte(), 0x72.toByte(), 0x79.toByte(), 0x3d.toByte(), 0x6c.toByte(), 0x6f.toByte(),
            0x73.toByte(), 0x73.toByte(), 0x2c.toByte(), 0x64.toByte(), 0x75.toByte(), 0x70.toByte(), 0x2c.toByte(),
            0x6a.toByte(), 0x69.toByte(), 0x74.toByte(), 0x74.toByte(), 0x2c.toByte(), 0x54.toByte(), 0x54.toByte(),
            0x4c.toByte(), 0x20.toByte(), 0x76.toByte(), 0x6f.toByte(), 0x69.toByte(), 0x70.toByte(), 0x2d.toByte(),
            0x6d.toByte(), 0x65.toByte(), 0x74.toByte(), 0x72.toByte(), 0x69.toByte(), 0x63.toByte(), 0x73.toByte(),
            0x0d.toByte(), 0x0a.toByte(), 0x6d.toByte(), 0x3d.toByte(), 0x61.toByte(), 0x75.toByte(), 0x64.toByte(),
            0x69.toByte(), 0x6f.toByte(), 0x20.toByte(), 0x37.toByte(), 0x30.toByte(), 0x37.toByte(), 0x38.toByte(),
            0x20.toByte(), 0x52.toByte(), 0x54.toByte(), 0x50.toByte(), 0x2f.toByte(), 0x41.toByte(), 0x56.toByte(),
            0x50.toByte(), 0x20.toByte(), 0x39.toByte(), 0x36.toByte(), 0x20.toByte(), 0x38.toByte(), 0x20.toByte(),
            0x33.toByte(), 0x20.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x20.toByte(), 0x39.toByte(),
            0x37.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x61.toByte(), 0x3d.toByte(), 0x72.toByte(), 0x74.toByte(),
            0x70.toByte(), 0x6d.toByte(), 0x61.toByte(), 0x70.toByte(), 0x3a.toByte(), 0x39.toByte(), 0x36.toByte(),
            0x20.toByte(), 0x6f.toByte(), 0x70.toByte(), 0x75.toByte(), 0x73.toByte(), 0x2f.toByte(), 0x34.toByte(),
            0x38.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x2f.toByte(), 0x32.toByte(), 0x0d.toByte(),
            0x0a.toByte(), 0x61.toByte(), 0x3d.toByte(), 0x66.toByte(), 0x6d.toByte(), 0x74.toByte(), 0x70.toByte(),
            0x3a.toByte(), 0x39.toByte(), 0x36.toByte(), 0x20.toByte(), 0x75.toByte(), 0x73.toByte(), 0x65.toByte(),
            0x69.toByte(), 0x6e.toByte(), 0x62.toByte(), 0x61.toByte(), 0x6e.toByte(), 0x64.toByte(), 0x66.toByte(),
            0x65.toByte(), 0x63.toByte(), 0x3d.toByte(), 0x31.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x61.toByte(),
            0x3d.toByte(), 0x72.toByte(), 0x74.toByte(), 0x70.toByte(), 0x6d.toByte(), 0x61.toByte(), 0x70.toByte(),
            0x3a.toByte(), 0x31.toByte(), 0x30.toByte(), 0x31.toByte(), 0x20.toByte(), 0x74.toByte(), 0x65.toByte(),
            0x6c.toByte(), 0x65.toByte(), 0x70.toByte(), 0x68.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x65.toByte(),
            0x2d.toByte(), 0x65.toByte(), 0x76.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x2f.toByte(),
            0x34.toByte(), 0x38.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(),
            0x61.toByte(), 0x3d.toByte(), 0x72.toByte(), 0x74.toByte(), 0x70.toByte(), 0x6d.toByte(), 0x61.toByte(),
            0x70.toByte(), 0x3a.toByte(), 0x39.toByte(), 0x37.toByte(), 0x20.toByte(), 0x74.toByte(), 0x65.toByte(),
            0x6c.toByte(), 0x65.toByte(), 0x70.toByte(), 0x68.toByte(), 0x6f.toByte(), 0x6e.toByte(), 0x65.toByte(),
            0x2d.toByte(), 0x65.toByte(), 0x76.toByte(), 0x65.toByte(), 0x6e.toByte(), 0x74.toByte(), 0x2f.toByte(),
            0x38.toByte(), 0x30.toByte(), 0x30.toByte(), 0x30.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x61.toByte(),
            0x3d.toByte(), 0x72.toByte(), 0x74.toByte(), 0x63.toByte(), 0x70.toByte(), 0x2d.toByte(), 0x66.toByte(),
            0x62.toByte(), 0x3a.toByte(), 0x2a.toByte(), 0x20.toByte(), 0x63.toByte(), 0x63.toByte(), 0x6d.toByte(),
            0x20.toByte(), 0x74.toByte(), 0x6d.toByte(), 0x6d.toByte(), 0x62.toByte(), 0x72.toByte(), 0x0d.toByte()
        )
    }

    @Test
    fun `Send some packets trough the entire pipeline to SIP3 Salto`() {
        val dev = Pcaps.findAllDevs().first {
            !it.isLoopBack
                    && it.addresses.any { addr -> addr.address.address.size <= 4 && addr.broadcastAddress != null }
                    && it.linkLayerAddresses.isNotEmpty()
        }
        val broadcastAddress = dev.addresses
            .first { addr -> addr.address.address.size <= 4 }
            .broadcastAddress
        val remotePort = findRandomPort()

        val loopback = InetAddress.getLoopbackAddress()
        val address = loopback.hostAddress
        val port = findRandomPort()

        runTest(
            deploy = {
                vertx.deployTestVerticle(Bootstrap::class, JsonObject().apply {
                    put("pcap", JsonObject().apply {
                        put("dev", dev.name)
                        put("bpf_filter", "udp and port $remotePort")
                        put("timeout_millis", 10)
                    })
                    put("sender", JsonObject().apply {
                        put("uri", "udp://$address:$port")
                    })
                })
            },
            execute = {
                vertx.setPeriodic(1000) {
                    val options = datagramSocketOptionsOf(broadcast = true)
                    vertx.createDatagramSocket(options).send(Buffer.buffer(PACKET), remotePort, broadcastAddress.hostAddress)
                }
            },
            assert = {
                vertx.createDatagramSocket()
                    .handler { packet ->
                        val buffer = packet.data().byteBuf
                        context.verify {
                            assertEquals(990, buffer.writerIndex())
                            // Prefix
                            var prefix = ByteArray(4)
                            buffer.readBytes(prefix)
                            assertArrayEquals(Encoder.PREFIX, prefix)
                            // Version
                            assertEquals(2, buffer.readByte())
                            // Compressed
                            assertEquals(0, buffer.readByte())
                            // Type
                            assertEquals(1, buffer.readByte())
                            // Version
                            assertEquals(1, buffer.readByte())
                            // Length
                            assertEquals(984, buffer.readShort())
                            // Milliseconds
                            assertEquals(1, buffer.readByte())
                            assertEquals(11, buffer.readShort())
                            buffer.skipBytes(8)
                            // Nanoseconds
                            assertEquals(2, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            buffer.skipBytes(4)
                            // Source Address
                            assertEquals(3, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            val srcAddr = ByteArray(4)
                            buffer.readBytes(srcAddr)
                            // Destination Address
                            assertEquals(4, buffer.readByte())
                            assertEquals(7, buffer.readShort())
                            val dstAddr = ByteArray(4)
                            buffer.readBytes(dstAddr)
                            assertArrayEquals(broadcastAddress.address, dstAddr)
                            // Source Port
                            assertEquals(5, buffer.readByte())
                            assertEquals(5, buffer.readShort())
                            buffer.skipBytes(2)
                            // Destination Port
                            assertEquals(6, buffer.readByte())
                            assertEquals(5, buffer.readShort())
                            assertEquals(remotePort, buffer.readUnsignedShort())
                            // Protocol Code
                            assertEquals(7, buffer.readByte())
                            assertEquals(4, buffer.readShort())
                            assertEquals(ProtocolCodes.SIP, buffer.readByte())
                            // Payload
                            assertEquals(8, buffer.readByte())
                            assertEquals(934, buffer.readShort())
                        }
                        context.completeNow()
                    }
                    .listen(port, address) {}
            }
        )
    }
}