package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.TimeUnit

class SocketForwardHandler(val host: String, val port: Int, val timeoutSeconds: Long = 60) : SocketHandler {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  val LB: Byte = 0x13
  val sleepInMs = 1000L
  var run = true

  override fun start(socket: Socket) {
    LOG.info("Connection accepted on port {}. Start forwarding to {}:{}.", socket.port, host, port)
    val inChannel = Channels.newChannel(socket.getInputStream())
    val outChannel = Channels.newChannel(socket.getOutputStream())

    val client = SocketClient()

    var emptyCounter = TimeUnit.SECONDS.toMillis(timeoutSeconds) / sleepInMs
    val inBuffer = ByteBuffer.allocate(1024)
    while(run) {
      val amount = inChannel.read(inBuffer)
      if(amount == 0) {
        if(emptyCounter-- == 0L) {
          run = false
        }
        LOG.trace("Waiting for data ({})...", emptyCounter)
        Thread.sleep(sleepInMs)
      } else {
        LOG.trace("Received '{}' bytes of data", amount)
        inBuffer.flip()
        val stream = ContentHelper.toStream(inBuffer)
        LOG.trace("Received: '{}'", stream.asString())
        if(inBuffer[0] == LB) {
          run = false
        } else {
          val response = client.sendTo(host, port, inBuffer)
          LOG.trace("Forwarded: '{}'", stream.asString())
          val responseStream = ContentHelper.toStream(response)
          LOG.trace("Got response (from: {}:{}): {}", host, port, responseStream.asString())
          LOG.trace("Write response back for socket {}:{}", socket.inetAddress.hostName, socket.port)
          outChannel.write(response)
        }
      }

    }
  }

  fun stop() {
    run = false
  }
}