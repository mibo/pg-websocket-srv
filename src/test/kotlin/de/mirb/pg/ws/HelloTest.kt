package de.mirb.pg.ws

import de.mirb.pg.socks.WebSocketHandler
import de.mirb.pg.util.ContentHelper
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import java.nio.ByteBuffer

class HelloTest : StringSpec() {
  init {
    "length should return size of string" {
      val hw = HelloWorld()
      hw.sayHello("World") shouldBe "Hello World"
    }
    /*
      Octet i of the transformed data ("transformed-octet-i") is the XOR of
      octet i of the original data ("original-octet-i") with octet at index
      i modulo 4 of the masking key ("masking-key-octet-j"):

          j = i MOD 4
          transformed-octet-i = original-octet-i XOR masking-key-octet-j
     */
    "unmasking for websocket payload" {
      // Masking-Key: 4e 1f e6 4f (`4e1fe64f`)
      // Masked payload: 1a 7a 95 3b (`1a7a953b`)
      // UnMasked payload: Test
//      var input = "e6"
//      val lon = input.toLong(16)
//      val ba = "4e1fe64fff00".hexStringToByteArray()
//      println("toLo: " + lon + " hs: " + ba.map { it.toString() })

      val wsh = WebSocketHandler()
      val unmasked = wsh.applyMask("4e1fe64f".hexStringToByteArray(), "1a7a953b".hexStringToByteArray())
//      println("Unmasked: " + String(unmasked))
      String(unmasked) shouldBe "Test"
    }
    "unwrap of websocket payload" {
      //WebSocket
//      1... .... = Fin: True
//      .000 .... = Reserved: 0x00
//      .... 0001 = Opcode: Text (1)
//      1... .... = Mask: True
//      .000 0100 = Payload length: 4
//      Masking-Key: 4e1fe64f
      val buffer = ByteBuffer.wrap("81844e1fe64f1a7a953b".hexStringToByteArray())
      val wsh = WebSocketHandler()
      val unwrappedPayload = wsh.unwrap(buffer)
      ContentHelper.asString(unwrappedPayload.content) shouldBe "Test"
      unwrappedPayload.binary shouldBe false
    }
    "wrap of websocket payload" {
      //WebSocket
//      val buffer = ByteBuffer.wrap("81844e1fe64f1a7a953b".hexStringToByteArray())
      val content = ByteBuffer.wrap("Test".toByteArray())
      val wsh = WebSocketHandler()
      val wrappedPayload = wsh.wrap(false, content)
      wrappedPayload[0] shouldBe 1.toByte()
      wrappedPayload[1] shouldBe (-124).toByte()
      wrappedPayload.position(2)
      ContentHelper.asString(wrappedPayload.slice()) shouldBe "Test"
    }
  }

  fun String.hexStringToByteArray(): ByteArray {
    val result = ByteArray(length / 2)

    for ((pos, i) in (0 until length step 2).withIndex()) {
      val t = StringBuilder()
      t.append(this[i])
      t.append(this[i+1])

      val signedByte = t.toString().toLong(16) - 128
      result[pos] = signedByte.toByte()
    }

    return result

  }
    /*
    private val HEX_CHARS = "0123456789ABCDEF"
    fun String.hexStringToByteArray() : ByteArray {

      val result = ByteArray(length / 2)

      for (i in 0 until length step 2) {
        val firstIndex = HEX_CHARS.indexOf(this[i]);
        val secondIndex = HEX_CHARS.indexOf(this[i + 1]);

        val octet = firstIndex.shl(4).or(secondIndex)
        result.set(i.shr(1), octet.toByte())
      }

      return result
    }
    */
}