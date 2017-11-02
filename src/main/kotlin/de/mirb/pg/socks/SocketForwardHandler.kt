package de.mirb.pg.socks

import de.mirb.pg.util.ContentHelper
import org.slf4j.LoggerFactory
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.util.concurrent.TimeUnit

class SocketForwardHandler(
    private val host: String,
    private val port: Int,
    private val timeoutSeconds: Long = 10) : SocketHandler {

  private val LOG = LoggerFactory.getLogger(this.javaClass.name)
  private val sleepInMs = 200L
  private val daemonMode: Boolean = timeoutSeconds < 0
  private var run = true

  override fun start(socket: Socket) {
    LOG.info("Connection accepted on port {}. Start forwarding to {}:{}.", socket.port, host, port)
    val inChannel = Channels.newChannel(socket.getInputStream())
    val outChannel = Channels.newChannel(socket.getOutputStream())
    run = true

    val client = SocketClient(host, port)

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
        if(!daemonMode && emptyCounter-- <= 0L) {
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
        val response = client.send(inBuffer)
        LOG.trace("Forwarded: '{}'", stream.asString())
        val responseStream = ContentHelper.toStream(response)
        LOG.trace("Got response (from: {}:{}): {}", host, port, responseStream.asString())
        LOG.trace("Write response back for socket {}:{}", socket.inetAddress.hostName, socket.port)
        outChannel.write(response)
        inBuffer.clear()
      }
    }

    LOG.info("Close channels/client/socket for forward handler")
    inChannel.close()
    outChannel.close()
    socket.close()
    client.close()
  }

  override fun stop() {
    run = false
  }
}