package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

interface SocketClient {
  fun send(content: String, charset: Charset = StandardCharsets.UTF_8): String {
    val byteContent = content.toByteArray(charset)
    val response = send(ByteBuffer.wrap(byteContent))
    return ContentHelper.toStream(response).asString(charset)
  }

  fun send(content: ByteBuffer): ByteBuffer

  fun connection(): String
}