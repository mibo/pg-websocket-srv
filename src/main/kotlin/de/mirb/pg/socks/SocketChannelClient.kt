package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class SocketChannelClient(private val host: String, private val port: Int, private val blocking: Boolean = true): SocketClient {
  private val BUFFER_CAP = 1024 * 64
  private val log = LoggerFactory.getLogger(this.javaClass.name)
  private val socket = SocketChannel.open()

  init {
    socket.configureBlocking(blocking)
    socket.connect(InetSocketAddress(host, port))
    log.debug("Connected to '{}:{}' (with blocking={};).", host, port, blocking)
  }

  override fun connection(): String {
    return host + ":" + port
  }

  fun isClosed(): Boolean {
    return !socket.isOpen
  }

  override fun send(content: ByteBuffer): ByteBuffer {
    try {
      while(!socket.finishConnect()) {
        // wait for connection finished
        log.trace("Wait (blocking) for connection finished (to {}:{})...", host, port)
        Thread.sleep(50)
      }
      log.trace("Send {} bytes of data", content.limit())
      socket.write(content)

      log.debug("Wait (blocking) for response (from {}:{})...", host, port)

      val read = ByteBuffer.allocate(BUFFER_CAP)
      var receivedBytesCount = socket.read(read)
      if(receivedBytesCount == 0) {
        // do one re-read after a short sleep
        log.trace("Re-read (from {}:{})...", host, port)
        Thread.sleep(100)
        receivedBytesCount = socket.read(read)
      }

      when {
        receivedBytesCount < 0 -> {// EOF
          log.trace("Received EOF. Closing socket.")
          close()
        }
        receivedBytesCount == 0 -> // no data
          log.trace("Received no data. Closing socket.")
        else ->
          log.trace("Received {} bytes with content: {}", receivedBytesCount, ContentHelper.toStream(read).asString())
      }

      read.flip()
      return read
    } catch (e: IOException) {
      log.error("Exception occurred: " + e.message, e)
      close()
      return ByteBuffer.allocate(0)
    }
  }

  fun close() {
    log.info("Close channels and socket.")
    socket.close()
  }
}