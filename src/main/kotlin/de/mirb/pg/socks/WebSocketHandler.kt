package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.*
import kotlin.experimental.and
import kotlin.experimental.xor

class WebSocketHandler {

  class WebSocketPayload(val binary: Boolean, val content: ByteBuffer)

  /* https://tools.ietf.org/html/rfc6455.html#section-5.2
      0                   1                   2                   3
      0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     +-+-+-+-+-------+-+-------------+-------------------------------+
     |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
     |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
     |N|V|V|V|       |S|             |   (if payload len==126/127)   |
     | |1|2|3|       |K|             |                               |
     +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
     |     Extended payload length continued, if payload len == 127  |
     + - - - - - - - - - - - - - - - +-------------------------------+
     |                               |Masking-key, if MASK set to 1  |
     +-------------------------------+-------------------------------+
     | Masking-key (continued)       |          Payload Data         |
     +-------------------------------- - - - - - - - - - - - - - - - +
     :                     Payload Data continued ...                :
     + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     |                     Payload Data continued ...                |
     +---------------------------------------------------------------+
   */

  private var open = false

  fun isOpen(): Boolean {
    return open
  }

  fun wrap(binary: Boolean, content: ByteBuffer): ByteBuffer {
    // basic implementation w/o mask and max len 124
    // TODO: implement mask
    // TODO: support for payload > 124
    val response = ByteBuffer.allocate(content.remaining() + 4)
    if(binary) {
      response.put(2)
    } else {
      response.put(1)
    }
    if(content.limit() < 124) {
      val len = content.limit().toByte()-128
      response.put(len.toByte())
    } else {
      // TODO: do correct payload length calculation
    }
    response.put(content)
    response.flip()

    return response
  }

  fun unwrap(content: ByteBuffer): WebSocketPayload {
    val finOpCode = content[0]
    // TODO: fix this opcode handling
    val isText = finOpCode and 0x01
    val isBinary = finOpCode and 0x02
    val binary = isBinary > 0 && isText.toInt() == 0
    val payloadLen = extractPayloadLength(content)
    // TODO: fix extended payload len / masking key start
    if(payloadLen.mask) {
      val maskingKey = ByteArray(4)
      val startIndex = payloadLen.nextPos
      val endIndex = startIndex + 4
      for ((pos, i) in (startIndex until endIndex).withIndex()) {
        maskingKey[pos] = content[i]
      }
      val maskedPayload = ByteArray(payloadLen.length)
      content.position(endIndex)
      content.get(maskedPayload, 0, payloadLen.length)
      return WebSocketPayload(binary, ByteBuffer.wrap(applyMask(maskingKey, maskedPayload)))
    } else {
      val payload = ByteArray(payloadLen.length)
      content.position(payloadLen.nextPos)
      content.get(payload, 0, payloadLen.length)
      return WebSocketPayload(binary, ByteBuffer.wrap(payload))
    }
  }

  /**
   * Mask:  1 bit

  Defines whether the "Payload data" is masked.  If set to 1, a
  masking key is present in masking-key, and this is used to unmask
  the "Payload data" as per Section 5.3.  All frames sent from
  client to server have this bit set to 1.

  Payload length:  7 bits, 7+16 bits, or 7+64 bits

  The length of the "Payload data", in bytes: if 0-125, that is the
  payload length.  If 126, the following 2 bytes interpreted as a
  16-bit unsigned integer are the payload length.  If 127, the
  following 8 bytes interpreted as a 64-bit unsigned integer (the
  most significant bit MUST be 0) are the payload length.  Multibyte
  length quantities are expressed in network byte order.  Note that
  in all cases, the minimal number of bytes MUST be used to encode
  the length, for example, the length of a 124-byte-long string
  can't be encoded as the sequence 126, 0, 124.  The payload length
  is the length of the "Extension data" + the length of the
  "Application data".  The length of the "Extension data" may be
  zero, in which case the payload length is the length of the
  "Application data".
   */
  private fun extractPayloadLength(content: ByteBuffer): PayloadInfo {
    val firstByte = content[1]
    // TODO: fix extended payload len / masking key start
    var len = firstByte.toPositiveInt()
    var mask = false
    var nextPos = 2
    if(len > 127) {
      mask = true
      len -= 128
    }
    if(len == 126) {
      nextPos = 4
      // TODO: fix extended payload len
    } else if(len == 127) {
      nextPos = 10
      // TODO: fix extended payload len
    }
    return PayloadInfo(mask, len, nextPos)
  }

  fun applyMask(mask: ByteArray, content: ByteArray): ByteArray {
    val result = ByteArray(content.size)
    for (i in 0 until content.size) {
      result[i] = content[i] xor mask[i%4]
    }
    return result
  }

  fun isWebSocketRequest(stream: ContentHelper.Stream): Boolean {
    if(stream.asString().startsWith("GET")) {
      return true
    }
    return false
  }

  private val SEC_WS_KEY_HEADER = "Sec-WebSocket-Key"
  private val HTTP_400_BAD_REQUEST_STATUS = "HTTP/1.1 400 Bad Request"

  fun createWebSocketResponse(stream: ContentHelper.Stream, flagAsOpen: Boolean = true): ByteBuffer {
    open = flagAsOpen
    val lines = stream.asString().lines()
    val secWebSocketKey = lines.singleOrNull { l -> l.startsWith(SEC_WS_KEY_HEADER) }
    // handle failure
    if(secWebSocketKey == null) {
      return ByteBuffer.wrap(HTTP_400_BAD_REQUEST_STATUS.toByteArray())
    }
    val value = secWebSocketKey.substring(SEC_WS_KEY_HEADER.length+1).trim() + WS_GUID
    val md = MessageDigest.getInstance("SHA1")
    val digest = md.digest(value.toByteArray(StandardCharsets.ISO_8859_1))
    val secWsAcceptValue = String(Base64.getEncoder().encode(digest))
    val res = WS_RESPONSE_TEMPLATE.replace("<accept>", secWsAcceptValue)
    val tmp = res.toByteArray(StandardCharsets.US_ASCII)
    return ByteBuffer.wrap(tmp)
  }

  // https://tools.ietf.org/html/rfc6455.html#section-1.3
  private val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
  private val EOL = "\r\n"
  private val WS_RESPONSE_TEMPLATE =
          "HTTP/1.1 101 Switching Protocols" + EOL +
                  "Server: mibo pg websocket" + EOL +
                  "Connection: Upgrade" + EOL +
                  "Upgrade: websocket" + EOL +
                  "Sec-WebSocket-Version: 13" + EOL +
                  "Sec-WebSocket-Accept: <accept>" + EOL + EOL

  fun createBadRequestResponse(): ByteBuffer {
    return ByteBuffer.wrap(HTTP_400_BAD_REQUEST_STATUS.toByteArray())
  }

  fun Byte.toPositiveInt(): Int {
    return this.toInt() + 128
  }

  class PayloadInfo(val mask: Boolean, val length: Int, val nextPos: Int)

  fun close() {
    open = false
  }
}