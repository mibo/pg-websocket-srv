package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.TimeUnit

class SocketBounceHandler(val timeoutSeconds: Long = 10) : SocketHandler {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  val LB: Byte = 0x13
  val sleepInMs = 1000L

  override fun start(socket: Socket) {
//    val socket = SocketChannel.open()
    LOG.info("Connection accepted on port {}", socket.port)
    val inChannel = Channels.newChannel(socket.getInputStream())
    val outChannel = Channels.newChannel(socket.getOutputStream())

    var run = true
    var emptyCounter = TimeUnit.SECONDS.toMillis(timeoutSeconds) / sleepInMs
    val inBuffer = ByteBuffer.allocate(1024)
    while(run) {
      val amount = inChannel.read(inBuffer)
      if(amount == 0) {
        if(--emptyCounter == 0L) {
          run = false
        }
        LOG.trace("Waiting for data ({})...", emptyCounter)
        Thread.sleep(sleepInMs)
//        LOG.trace("No data received")
      } else {
        LOG.trace("Received '{}' bytes of data", amount)
        inBuffer.flip()
        val stream = ContentHelper.toStream(inBuffer)
        LOG.trace("Received: '{}'", stream.asString())
        if(inBuffer[0] == LB) {
          run = false
        } else {
          val bounced = outChannel.write(inBuffer)
          LOG.trace("Bounced '{}' bytes of data", bounced)
        }
      }
    }
    socket.close()
  }
}