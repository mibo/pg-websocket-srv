package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.TimeUnit

class SocketBounceHandler(private val timeoutSeconds: Long = 10) : SocketHandler {
  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val sleepInMs = 200L
  private val daemonMode: Boolean = timeoutSeconds < 0
  private var run = true

  override fun start(socket: Socket) {
    LOG.info("Connection accepted on port {}", socket.port)
    val inChannel = Channels.newChannel(socket.getInputStream())
    val outChannel = Channels.newChannel(socket.getOutputStream())

    run = true
    var emptyCounter = TimeUnit.SECONDS.toMillis(timeoutSeconds) / sleepInMs
    val inBuffer = ByteBuffer.allocate(1024)
    LOG.debug("Wait for input data (run={})...", run)
    while(run) {
      val amount = inChannel.read(inBuffer)
      if(amount == -1) {
        // EOF
        LOG.trace("Received EOF.")
        run = false
      } else if(amount == 0) {
        if(!daemonMode && --emptyCounter <= 0L) {
          run = false
          continue
        }
        LOG.trace("Waiting for data (daemon={}, counter={}, sleep={})...", daemonMode, emptyCounter, sleepInMs)
        Thread.sleep(sleepInMs)
      } else {
        LOG.trace("Received '{}' bytes of data", amount)
        inBuffer.flip()
        val stream = ContentHelper.toStream(inBuffer)
        LOG.trace("Received: '{}'", stream.asString())
        val bounced = outChannel.write(inBuffer)
        LOG.trace("Bounced '{}' bytes of data", bounced)
        inBuffer.clear()
      }
    }
    LOG.info("Close socket for bounce handler")
    socket.close()
  }

  override fun stop() {
    run = false
  }
}