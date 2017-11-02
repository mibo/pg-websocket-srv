package de.mirb.pg.socks

import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class SocketClient {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)

  fun sendTo(host: String, port: Int, content: String): ByteBuffer? {
    return sendToOld(host, port, ByteBuffer.wrap(content.toByteArray()))
  }

  fun sendTo(host: String, port: Int, content: ByteBuffer): ByteBuffer {
    try {
      val socket = SocketChannel.open()
      socket.configureBlocking(false)
      val selector = Selector.open()
      val key = socket.register(selector, SelectionKey.OP_CONNECT, this)
      val sa = InetSocketAddress(host, port)
      socket.connect(sa)
      socket.finishConnect()
      LOG.trace("Connected to '{}:{}'.", host, port)
      socket.write(content)
      LOG.trace("Send {} bytes of data", content.limit())

      val read = ByteBuffer.allocate(1024)
      var receivedBytesCount = socket.read(read)
      LOG.trace("Received {} bytes of data", receivedBytesCount)
      while (receivedBytesCount >= 0) {
        // TODO: handle read more then buffer size
        receivedBytesCount = socket.read(read)
      }
      read.flip()
      LOG.trace("Received content: {}", readToString(read))
      return read
    } catch (e: IOException) {
      e.printStackTrace()
      LOG.error("Exception occurred: " + e.message, e)
      return ByteBuffer.allocate(0)
    }
  }

  fun sendToOld(host: String, port: Int, content: ByteBuffer): ByteBuffer? {
    try {
      val socket = Socket(host, port)
//      val sa = InetSocketAddress(host, port)
//      socket.connect(sa)
      LOG.trace("Connected to '{}:{}'.", host, port)
      val inChannel = Channels.newChannel(socket.getInputStream())
      val outChannel = Channels.newChannel(socket.getOutputStream())
      LOG.trace("Send {} bytes of data", content.limit())
      outChannel.write(content)

      val read = ByteBuffer.allocate(1024)
      var receivedBytesCount = inChannel.read(read)
      LOG.trace("Received {} bytes of data", receivedBytesCount)
      while (receivedBytesCount >= 0) {
        // handle read more then buffer size
        LOG.trace("Wait for EOF...")
        receivedBytesCount = inChannel.read(read)
      }
      read.flip()
      LOG.trace("Received content: {}", readToString(read))
      socket.close()
      return read
    } catch (e: IOException) {
      e.printStackTrace()
      LOG.error("Exception occurred: " + e.message, e)
      return ByteBuffer.allocate(0)
    }
  }

  fun readToString(buffer: ByteBuffer): String {
    val tmpBuffer = buffer.duplicate()
    val tmp: ByteArray = kotlin.ByteArray(tmpBuffer.limit())
    tmpBuffer.get(tmp)
    return String(tmp)
  }
}
