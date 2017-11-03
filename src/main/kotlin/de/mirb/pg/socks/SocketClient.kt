package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class SocketClient(private val host: String, private val port: Int) {
  private val BUFFER_CAP = 1024 * 64
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val inChannel: ReadableByteChannel
  private val outChannel: WritableByteChannel
  private val socket = Socket(host, port)

  init {
    LOG.trace("Connected to '{}:{}'.", host, port)

    inChannel = Channels.newChannel(socket.getInputStream())
    outChannel = Channels.newChannel(socket.getOutputStream())
  }

  fun connection(): String {
    return host + ":" + port
  }

  fun send(content: String, charset: Charset = StandardCharsets.UTF_8): String {
    val byteContent = content.toByteArray(charset)
    val response = send(ByteBuffer.wrap(byteContent))
    return ContentHelper.toStream(response).asString(charset)
  }

  fun send(content: ByteBuffer): ByteBuffer {
    try {
      LOG.trace("Send {} bytes of data", content.limit())
      outChannel.write(content)

      LOG.debug("Wait (blocking) for response (from {}:{})...", host, port)

      val read = ByteBuffer.allocate(BUFFER_CAP)
      var receivedBytesCount = inChannel.read(read)
      if(receivedBytesCount == 0) {
        // do one re-read after a short sleep
        LOG.trace("Re-read (from {}:{})...", host, port)
        Thread.sleep(100)
        receivedBytesCount = inChannel.read(read)
      }
      LOG.trace("Received {} bytes of data", receivedBytesCount)

      when {
        receivedBytesCount < 0 -> // EOF
          LOG.trace("Received EOF. Closing socket.")
        receivedBytesCount == 0 -> // no data
          LOG.trace("Received no data. Closing socket.")
        else ->
          LOG.trace("Received content: {}", ContentHelper.toStream(read).asString())
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
    inChannel.close()
    outChannel.close()
    socket.close()
  }
}