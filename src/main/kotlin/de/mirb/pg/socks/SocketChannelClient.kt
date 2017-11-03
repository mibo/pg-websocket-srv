package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.SocketChannel
import java.nio.channels.WritableByteChannel

class SocketChannelClient(private val host: String, private val port: Int, private val blocking: Boolean = true): SocketClient {
  private val BUFFER_CAP = 1024 * 64
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val socket = SocketChannel.open()

  init {
    socket.configureBlocking(blocking)
    socket.connect(InetSocketAddress(host, port))
    LOG.trace("Connected to '{}:{}' (with blocking={};).", host, port, blocking)
  }

  override fun connection(): String {
    return host + ":" + port
  }

  override fun send(content: ByteBuffer): ByteBuffer {
    try {
      while(!socket.finishConnect()){
        // wait for connection finished
        LOG.trace("Wait (blocking) for connection finished (to {}:{})...", host, port)
        Thread.sleep(50)
      }
      LOG.trace("Send {} bytes of data", content.limit())
      socket.write(content)

      LOG.debug("Wait (blocking) for response (from {}:{})...", host, port)

      val read = ByteBuffer.allocate(BUFFER_CAP)
      var receivedBytesCount = socket.read(read)
      if(receivedBytesCount == 0) {
        // do one re-read after a short sleep
        LOG.trace("Re-read (from {}:{})...", host, port)
        Thread.sleep(100)
        receivedBytesCount = socket.read(read)
      }

      when {
        receivedBytesCount < 0 -> // EOF
          LOG.trace("Received EOF. Closing socket.")
        receivedBytesCount == 0 -> // no data
          LOG.trace("Received no data. Closing socket.")
        else ->
          LOG.trace("Received {} bytes with content: {}", receivedBytesCount, ContentHelper.toStream(read).asString())
      }

      read.flip()
      return read
    } catch (e: IOException) {
      e.printStackTrace()
      LOG.error("Exception occurred: " + e.message, e)
      close()
      return ByteBuffer.allocate(0)
    }
  }

  fun close() {
    LOG.info("Close channels and socket.")
    socket.close()
  }
}